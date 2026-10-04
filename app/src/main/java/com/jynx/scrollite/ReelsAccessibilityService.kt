package com.jynx.scrollite

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.graphics.Bitmap
import android.os.Build
import android.view.Display
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

import com.jynx.scrollite.data.AppDatabase
import com.jynx.scrollite.data.AppearanceStore
import com.jynx.scrollite.data.Platform
import com.jynx.scrollite.data.ReelEntry
import com.jynx.scrollite.data.ReelsRepository
import com.jynx.scrollite.data.RoomReelsRepository
import com.jynx.scrollite.data.ScreenTimeStore
import com.jynx.scrollite.data.Section
import com.jynx.scrollite.data.ThemeMode
import com.jynx.scrollite.data.UsageRepository
import com.jynx.scrollite.data.UsageSession
import com.jynx.scrollite.tracking.AppDetector
import com.jynx.scrollite.tracking.AppDetectors
import com.jynx.scrollite.tracking.Moment
import com.jynx.scrollite.tracking.IdleRecheck
import com.jynx.scrollite.tracking.LimitTimer
import com.jynx.scrollite.tracking.SessionTracker
import com.jynx.scrollite.tracking.Throttle
import com.jynx.scrollite.ui.limit.LimitOverlay
import com.jynx.scrollite.ui.limit.LimitOverlaySpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.ZoneId

// for later
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK

class ReelsAccessibilityService : AccessibilityService() {

    companion object {

        private const val TAG = "ReelsService"

        // App-specific signals (package names, view IDs) live in each app's AppDetector

        // short-video extraction
        private const val DEBOUNCE_MS = 400L

        //notification limits
        private const val LIMIT_CHANNEL_ID = "reels_limit_channel"
        private const val LIMIT_NOTIFICATION_ID = 1001
        
        // Instagram feed
        private const val ID_FEED_TAB = "com.instagram.android:id/feed_tab"
        private const val LAUNCH_REDIRECT_DELAY_MS = 500L // give the UI time to render before clicking

        // Detection reads the tracked app's screen tree (calls into that app's process),
        // and video playback alone fires many content-change events a second. Run it
        // at most this often; screen switches still run immediately. See Throttle.
        private const val DETECTION_INTERVAL_MS = 300L

        // Safety re-check while a session is open but the app has gone quiet,
        // so a stale reading (e.g. Reels caught mid-transition) can't keep
        // counting. Caps that error at this long; see IdleRecheck.
        private const val IDLE_RECHECK_MS = 5_000L

        // Section detection flickers for single frames during normal scrolling
        // (23 false readings observed within 86ms in one real session) — only treat
        // a section change as real if the new reading holds for this long.
        private const val SECTION_SWITCH_DEBOUNCE_MS = 500L

        // Eject verification/retry
        private const val EJECT_VERIFY_DELAY_MS = 300L
        private const val MAX_EJECT_ATTEMPTS = 3
    }

    // Debounce
    private val debounceHandler = Handler(Looper.getMainLooper())
    private val detectionThrottle = Throttle(DETECTION_INTERVAL_MS)
    private val trailingDetection = Runnable { detectNow() }
    private val idleRecheck = IdleRecheck(
        intervalMs = IDLE_RECHECK_MS,
        postDelayed = { runnable, delayMs -> debounceHandler.postDelayed(runnable, delayMs) },
        removeCallbacks = { runnable -> debounceHandler.removeCallbacks(runnable) },
        onRecheck = { detectNow() },
    )
    // Fires when the open short-form session reaches the limit; see scheduleLimitCheck()
    private val limitCheck = Runnable {
        checkLimitDuringSession()
        if (!hasBumpedThisSession) scheduleLimitCheck() // fired a little early; aim again
    }
    private var debounceRunnable: Runnable? = null
    private var pendingSwitchRunnable: Runnable? = null
    private var pendingSwitchTarget: Pair<Platform, Section>? = null

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository: ReelsRepository by lazy {
        RoomReelsRepository(AppDatabase.getInstance(applicationContext).reelDao(), applicationContext)
    }

    // Interim de-dup heuristic: insert only when the author changes from the
    // last inserted one. Not real debounce (won't handle scrolling back to a
    // previous Reel, rapid re-scrolls, etc.) — a placeholder until a proper
    // "confirmed new Reel" trigger exists.
    private var lastInsertedUsername: String? = null

    // The app + section currently being timed; each continuous stretch becomes a usage session
    private val sessionTracker = SessionTracker()
    private val usageRepository: UsageRepository by lazy {
        UsageRepository(AppDatabase.getInstance(applicationContext).usageSessionDao())
    }

    // Serializes session writes and limit-state reads (see launchInOrder)
    private val dbMutex = Mutex()

    private var lastForegroundPackage: String? = null

    // "Session" in the limit fields below means the current short-video stretch
    private var todayTotalAtSessionStart = 0L
    private var cachedDailyLimitMs = Long.MAX_VALUE // safe default until loaded
    private var cachedExtensionMs = 0L
    private var cachedExtensionsUsed = 0
    private var cachedMaxExtensions = 0
    private var cachedThemeMode = ThemeMode.DEFAULT
    private var hasBumpedThisSession = false
    private var limitStateLoadJob: Job? = null

    // Which load the cached limit values belong to; main thread only. Checks
    // wait for the current one — the previous session's values may be from
    // yesterday and would bump a user who's under today's limit.
    private var limitStateGeneration = 0
    private var loadedLimitStateGeneration = -1

    private val limitOverlay by lazy { LimitOverlay(this) }

    /** The app the limit overlay is shown over; leaving it for another app dismisses the overlay. */
    private var overlayAppPackage: String? = null


    /**
     * Only the event types listed in res/xml/accessibility_service_config.xml
     * arrive here (window state, window content, view scrolled). Add a type
     * there before relying on it in this handler.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        Log.d(TAG, "eventType=${AccessibilityEvent.eventTypeToString(event.eventType)} pkg=${event.packageName}")

        // Our own overlay window fires events too. Without this, showing it would
        // look like the user left the tracked app (the untracked-app branch below).
        if (event.packageName == packageName) return

        // Timing is already committed and the eject is owned by the overlay flow,
        // so ignore everything except the user leaving for another app — then
        // there's nothing left to eject from, so just take the overlay down.
        if (limitOverlay.isShowing) {
            if (event.packageName?.toString() != overlayAppPackage &&
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            ) {
                limitOverlay.dismiss()
            }
            return
        }

//        val currentPackage = event.packageName?.toString()
//        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
//            currentPackage == INSTAGRAM_PKG &&
//            lastForegroundPackage != INSTAGRAM_PKG &&
//            todayTotalAtSessionStart >= cachedDailyLimitMs
//        ) {
//            Log.d(TAG, "Instagram just came to foreground — will force-redirect to Feed")
//            forceRedirectToFeed()
//        }
        val detector = AppDetectors.forPackage(event.packageName)
        if (detector == null) {
            // Checking the active window (not just the event's package) keeps e.g. the
            // keyboard popping up in a tracked app's DMs from counting as leaving it
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && foregroundDetector() == null) {
                stopTracking()
            }
            return
        }
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            detectNow()
            return
        }
        // A skipped event becomes one run at the end of the window, so the latest
        // screen is always read — even when it was the last event of a burst
        when (val decision = detectionThrottle.request(SystemClock.uptimeMillis())) {
            Throttle.Decision.RunNow -> detectNow()
            is Throttle.Decision.RunLater -> debounceHandler.postDelayed(trailingDetection, decision.delayMs)
            Throttle.Decision.AlreadyScheduled -> Unit
        }
    }

    /**
     * Reads the front window and updates section timing, the limit check and
     * new-video extraction. Called straight from events or as the throttle's
     * trailing run, so it re-checks everything instead of trusting the event.
     */
    private fun detectNow() {
        cancelTrailingDetection()
        detectionThrottle.onRan(SystemClock.uptimeMillis())
        if (limitOverlay.isShowing) return

        val root = rootInActiveWindow ?: return
        // Apps keep sending events behind e.g. the notification shade — don't time those
        val detector = AppDetectors.forPackage(root.packageName)
        if (detector == null) {
            root.recycle()
            return
        }

        val section = detector.detectSection(root)
        Log.d(TAG, "${detector.platform} section=$section")
        onSectionDetected(detector.platform, section)
        idleRecheck.afterDetection(sessionOpen = sessionTracker.currentPlatform != null)

        if (section.isShortForm) {
            val author = detector.extractShortVideo(root)?.author
            if (author != null && author != lastInsertedUsername) {
                lastInsertedUsername = author
                scheduleExtraction(detector)
            }
        }  else {
            debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
        }

        root.recycle()
    }

    /** The detector for the app whose window is actually in front, if it's a tracked app. */
    private fun foregroundDetector(): AppDetector? {
        val root = rootInActiveWindow ?: return null
        val detector = AppDetectors.forPackage(root.packageName)
        root.recycle()
        return detector
    }

//    private fun forceRedirectToFeed() {
//        debounceHandler.postDelayed({
//            val root = rootInActiveWindow
//            val feedTab = root?.findAccessibilityNodeInfosByViewId(ID_FEED_TAB)?.firstOrNull()
//            if (feedTab != null) {
//                feedTab.performAction(AccessibilityNodeInfo.ACTION_CLICK)
//                Log.d(TAG, "Redirected to Feed tab on launch")
//            } else {
//                Log.w(TAG, "feed_tab not found on launch — UI may not have loaded yet")
//            }
//        }, LAUNCH_REDIRECT_DELAY_MS)
//    }

    override fun onServiceConnected(){
        super.onServiceConnected()
        val channel = NotificationChannel(
            LIMIT_CHANNEL_ID,
            "Short video time limit",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        // No window change fires when the screen turns off mid-Reel, so without this
        // the open session would keep counting until the next event — possibly hours later
        ContextCompat.registerReceiver(
            this, screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED
        )

        migrateLegacyTodayTotal()
    }

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "Screen off — ending the open session")
            stopTracking()
        }
    }

    /**
     * Today's watch time used to be a single number in ScreenTimeStore (it was
     * only ever Instagram Reels). Carry it over as one session ending now so today's limit doesn't reset on
     * upgrade (its exact times are unknown; only the total matters).
     */
    private fun migrateLegacyTodayTotal() {
        launchInOrder {
            val legacyMs = screenTimeStore.takeLegacyTodayTotal()
            if (legacyMs > 0) {
                val now = System.currentTimeMillis()
                usageRepository.record(
                    UsageSession(startMs = now - legacyMs, endMs = now, platform = Platform.INSTAGRAM, section = Section.SHORT_VIDEO)
                )
                Log.d(TAG, "Migrated ${legacyMs / 1000}s of today's watch time into usage sessions")
            }
        }
    }

    /**
     * Resets a short timer every time this is called. Only fires the actual
     * extraction once no new events have come in for DEBOUNCE_MS — i.e. once
     * the screen has "settled" instead of still mid-transition.
     */
    private fun scheduleExtraction(detector: AppDetector) {
        debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
        val runnable = Runnable {
            val freshRoot = rootInActiveWindow ?: return@Runnable

            // Re-check everything against the settled screen — the author
            // that triggered this might have already changed if the user
            // kept scrolling during the 400ms debounce window.
            if (freshRoot.packageName != detector.packageName || !detector.detectSection(freshRoot).isShortForm) {
                freshRoot.recycle()
                return@Runnable
            }

            val video = detector.extractShortVideo(freshRoot)
            if (video != null) {
                val entry = ReelEntry(
                    username = video.author,
                    caption = video.caption,
                    hashtags = extractHashtags(video.caption),
                    screenshotPath = null, // filled in after screenshot resolves
                    platform = detector.platform,
                    timestamp = System.currentTimeMillis(),
                )
                serviceScope.launch {
                    val rowId = repository.insert(entry) // needs to return the new row's id
                    triggerScreenshot(rowId)
                }
            }

            freshRoot.recycle()
        }
        debounceRunnable = runnable
        debounceHandler.postDelayed(runnable, DEBOUNCE_MS)
    }

    /**
     * Takes a screenshot of the current screen, guarded for API level and
     * handled asynchronously via callback. Never throws — failures are
     * logged and skipped, since this is a nice-to-have, not core detection.
     */
    private fun triggerScreenshot(rowId: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.d(TAG, "Skipping screenshot: requires API 30+")
            return
        }
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            mainExecutor,
            object : TakeScreenshotCallback {
                override fun onSuccess(result: ScreenshotResult) {
                    val bitmap = try {
                        Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to wrap screenshot buffer: ${e.message}")
                        null
                    } finally {
                        result.hardwareBuffer.close()
                    }
                    if (bitmap == null) return
                    serviceScope.launch {
                        val path = repository.saveScreenshot(rowId, bitmap) // Person 2's function
                        repository.updateScreenshotPath(rowId, path)
                    }
                }
                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "Screenshot failed, errorCode=$errorCode")
                }
            }
        )
    }

    private fun extractHashtags(caption: String?): List<String> {
        if (caption.isNullOrBlank()) return emptyList()
        return Regex("#\\w+").findAll(caption).map { it.value }.toList()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        // Otherwise a service disabled mid-countdown leaves the overlay stuck on screen
        limitOverlay.removeNow()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: IllegalArgumentException) {
            // Never registered (service destroyed before onServiceConnected)
        }
        stopTracking()
        super.onDestroy()
        serviceScope.cancel()
    }

    //Lazy so applicationContext access happens after service creation
    private val screenTimeStore: ScreenTimeStore by lazy {
        ScreenTimeStore(applicationContext)
    }

    private val appearanceStore: AppearanceStore by lazy {
        AppearanceStore(applicationContext)
    }

    /**
     * Called with the app + section detected on every event from a tracked app.
     * Starting from nothing, or switching to a different app, is immediate; a
     * change between sections of the same app is debounced (detection flickers
     * for single frames while scrolling) but dated from the first reading, so
     * the debounce delay isn't counted toward the wrong section.
     */
    private fun onSectionDetected(platform: Platform, section: Section) {
        val currentSection = sessionTracker.currentSection
        if (currentSection == null || sessionTracker.currentPlatform != platform) {
            cancelPendingSwitch()
            startSection(platform, section, sessionTracker.now())
            return
        }
        if (section == currentSection) {
            cancelPendingSwitch() // back on the current section after a flicker
            splitAtMidnightIfNeeded()
            if (section.isShortForm) checkLimitDuringSession()
            return
        }
        val target = platform to section
        if (pendingSwitchTarget == target) return // already waiting to switch here

        cancelPendingSwitch()
        val firstSeen = sessionTracker.now()
        val runnable = Runnable {
            pendingSwitchRunnable = null
            pendingSwitchTarget = null
            startSection(platform, section, firstSeen)
        }
        pendingSwitchRunnable = runnable
        pendingSwitchTarget = target
        debounceHandler.postDelayed(runnable, SECTION_SWITCH_DEBOUNCE_MS)
    }

    private fun startSection(platform: Platform, section: Section, at: Moment) {
        sessionTracker.switchTo(platform, section, at)?.let { persist(it) }
        if (section.isShortForm) {
            hasBumpedThisSession = false
            loadSessionLimitState()
        }
    }

    /**
     * Closes the open session at the first event after midnight and reopens it,
     * so the new day's limit check starts from the new day's total. (Queries
     * clip sessions to day bounds, so the closed one is still split correctly.)
     */
    private fun splitAtMidnightIfNeeded() {
        val since = sessionTracker.openSinceWallMs ?: return
        val startedOn = Instant.ofEpochMilli(since).atZone(ZoneId.systemDefault()).toLocalDate()
        if (startedOn == usageRepository.today()) return

        sessionTracker.restart()?.let { persist(it) }
        if (sessionTracker.currentSection?.isShortForm == true) {
            hasBumpedThisSession = false
            loadSessionLimitState()
        }
    }

    private fun cancelPendingSwitch() {
        pendingSwitchRunnable?.let { debounceHandler.removeCallbacks(it) }
        pendingSwitchRunnable = null
        pendingSwitchTarget = null
    }

    /** Ends the open session (left Instagram, screen off, bumped, service stopping). */
    private fun stopTracking() {
        // A pending trailing detection or re-check would otherwise restart timing after e.g. screen off
        cancelTrailingDetection()
        idleRecheck.stop()
        cancelPendingSwitch()
        debounceHandler.removeCallbacks(limitCheck)
        limitStateLoadJob?.cancel()
        sessionTracker.stop()?.let { persist(it) }
    }

    private fun cancelTrailingDetection() {
        debounceHandler.removeCallbacks(trailingDetection)
        detectionThrottle.cancelTrailing()
    }

    private fun persist(session: UsageSession) {
        launchInOrder {
            usageRepository.record(session)
            if (session.section.isShortForm) checkDailyLimit()
        }
    }

    /**
     * Runs database work strictly in call order. Without this, a session write
     * and the limit reload right after it race, and the reload can miss the
     * session it should include. UNDISPATCHED takes the (fair, FIFO) lock
     * synchronously at the call site, so lock order matches call order.
     */
    private fun launchInOrder(block: suspend () -> Unit): Job =
        serviceScope.launch(start = CoroutineStart.UNDISPATCHED) { dbMutex.withLock { block() } }

    /**
     * Loads the values checkLimitDuringSession() compares against for this
     * session. Cancels any load still in flight from a session that just
     * ended (e.g. a rapid stop/restart from detection flicker) so a stale
     * result can't land after this one and overwrite it with old data.
     */
    private fun loadSessionLimitState() {
        limitStateLoadJob?.cancel()
        val generation = ++limitStateGeneration
        limitStateLoadJob = launchInOrder {
            val total = usageRepository.shortFormTotalOn()
            val limit = screenTimeStore.getDailyLimit() * 60_000L
            val extension = screenTimeStore.getExtensionToday()
            val extensionsUsed = screenTimeStore.getExtensionsUsedToday()
            val maxExtensions = screenTimeStore.getStrictness().maxExtensionsPerDay
            todayTotalAtSessionStart = total
            cachedMaxExtensions = maxExtensions
            cachedThemeMode = appearanceStore.getThemeMode()
            cachedExtensionMs = extension
            cachedExtensionsUsed = extensionsUsed
            cachedDailyLimitMs = limit // written last — it's what arms checkLimitDuringSession()
            debounceHandler.post {
                // A load cancelled after its writes still posts this; only the current one arms checks
                if (generation == limitStateGeneration) {
                    loadedLimitStateGeneration = generation
                    scheduleLimitCheck()
                }
            }
        }
    }

    /**
     * Checks the limit at the moment the open session will cross it, instead
     * of waiting for the tracked app's next event — a playing video can go
     * seconds without one, so an event-only check showed the overlay late or,
     * if the user left first, not at all.
     */
    private fun scheduleLimitCheck() {
        debounceHandler.removeCallbacks(limitCheck)
        if (sessionTracker.currentSection?.isShortForm != true || hasBumpedThisSession) return
        val delayMs = LimitTimer.msUntilLimit(
            todayTotalAtSessionStart, sessionTracker.openDurationMs(), effectiveLimitMs()
        ) ?: return
        debounceHandler.postDelayed(limitCheck, delayMs)
    }

    /** The daily limit plus any extra time granted today. */
    private fun effectiveLimitMs(): Long =
        if (cachedDailyLimitMs == Long.MAX_VALUE) Long.MAX_VALUE else cachedDailyLimitMs + cachedExtensionMs

    private fun checkLimitDuringSession() {
        if (hasBumpedThisSession) return // don't spam performGlobalAction every event
        // The timed check can land after the session ended or moved off short-form
        if (sessionTracker.currentSection?.isShortForm != true) return
        if (loadedLimitStateGeneration != limitStateGeneration) return // still loading; the load schedules a check
        val projectedTotal = todayTotalAtSessionStart + sessionTracker.openDurationMs()
        if (projectedTotal >= effectiveLimitMs()) {
            bumpUserOutOfReels(projectedTotal)
        }
    }

    /**
     * Shows the limit overlay first and only ejects once its countdown ends
     * (or the user taps through), so the bump doesn't feel abrupt. The eject
     * happens behind the overlay, which is removed once it's verified.
     */
    private fun bumpUserOutOfReels(watchedMs: Long) {
        hasBumpedThisSession = true
        val platform = sessionTracker.currentPlatform ?: return
        val spec = LimitOverlaySpec.forBump(
            shortFormLabel = platform.labelFor(Section.SHORT_VIDEO),
            watchedMs = watchedMs,
            limitMs = cachedDailyLimitMs,
            extraMs = cachedExtensionMs,
            extensionsUsedToday = cachedExtensionsUsed,
            maxExtensionsPerDay = cachedMaxExtensions,
            alreadyOverAtSessionStart = todayTotalAtSessionStart >= effectiveLimitMs(),
        )
        Log.d(TAG, "Daily limit exceeded mid-session — showing ${spec.variant} overlay before eject")

        // Commit watch time up to now so the countdown itself isn't counted as Reels time
        debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
        stopTracking()

        overlayAppPackage = AppDetectors.all.firstOrNull { it.platform == platform }?.packageName
        limitOverlay.show(
            spec,
            themeMode = cachedThemeMode,
            onLeave = { attemptEject(attempt = 1) },
            onExtend = { minutes -> grantExtension(minutes) },
        )
    }

    /**
     * Saves the extension before taking the overlay down: once it's gone the
     * next Reels event starts a fresh session that reloads the limit state,
     * and it must see the extra time or it would bump the user straight back.
     * (Events are ignored while the overlay is up, so nothing races this.)
     */
    private fun grantExtension(minutes: Int) {
        Log.d(TAG, "User extended limit by $minutes min (extensions used today before this: $cachedExtensionsUsed)")
        serviceScope.launch(Dispatchers.Main) {
            screenTimeStore.grantExtension(minutes * 60_000L)
            limitOverlay.dismiss()
        }
    }

    /**
     * feed_tab (bottom nav) isn't present in the accessibility tree while the
     * Reels player is full-screen, so clicking it never has anything to find —
     * confirmed via logcat: it only ever appears in dumps taken on the Home
     * Feed screen. GLOBAL_ACTION_BACK is what actually has a chance of
     * working, but Instagram sometimes swallows it (e.g. to close an overlay)
     * instead of leaving Reels, so we verify and retry.
     */
    private fun attemptEject(attempt: Int) {
        performGlobalAction(GLOBAL_ACTION_BACK)

        debounceHandler.postDelayed({
            val root = rootInActiveWindow
            val stillInReels = root != null &&
                AppDetectors.forPackage(root.packageName)?.detectSection(root)?.isShortForm == true
            root?.recycle()

            if (!stillInReels) {
                limitOverlay.dismiss()
            } else if (attempt < MAX_EJECT_ATTEMPTS) {
                Log.w(TAG, "Eject attempt $attempt still in Reels — retrying")
                attemptEject(attempt + 1)
            } else {
                Log.w(TAG, "Eject failed after $MAX_EJECT_ATTEMPTS attempts, giving up for this session")
                limitOverlay.dismiss()
            }
        }, EJECT_VERIFY_DELAY_MS)
    }

    private suspend fun checkDailyLimit(){
        // Once a day — the overlay already covers every bump, so repeating the
        // notification (and its sound) on each Reels visit is just noise
        if (screenTimeStore.isNotifiedToday()) return
        val totalMs = usageRepository.shortFormTotalOn()
        val limitMs = screenTimeStore.getDailyLimit() * 60_000L
        if (totalMs >= limitMs) {
            showLimitNotification()
            screenTimeStore.markNotifiedToday()
        }
    }
    private fun showLimitNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "Skipping limit notification: POST_NOTIFICATIONS not granted")
            return
        }
        val notification = NotificationCompat.Builder(this, LIMIT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // TEMP placeholder icon, swap for a real one
            .setContentTitle("Daily Short-Form Content limit reached")
            .setContentText("You've hit your daily Short-Form Content watch time limit.")
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(LIMIT_NOTIFICATION_ID, notification)
    }


}