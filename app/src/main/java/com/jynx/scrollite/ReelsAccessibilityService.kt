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
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

import com.jynx.scrollite.data.AppDatabase
import com.jynx.scrollite.data.Platform
import com.jynx.scrollite.data.ReelEntry
import com.jynx.scrollite.data.ReelsRepository
import com.jynx.scrollite.data.RoomReelsRepository
import com.jynx.scrollite.data.ScreenTimeStore
import com.jynx.scrollite.ui.limit.LimitOverlay
import com.jynx.scrollite.ui.limit.LimitOverlaySpec
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// for later
import android.accessibilityservice.AccessibilityService.ScreenshotResult
import android.accessibilityservice.AccessibilityService.TakeScreenshotCallback
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK

class ReelsAccessibilityService : AccessibilityService() {

    companion object {

        // TEMP: UI exploration, remove before production
        private const val TAG = "ReelsService"
        private const val INSTAGRAM_PKG = "com.instagram.android"
        private const val DUMP_INTERVAL_MS = 3000L

        // Signals discovered via Layout Inspector / the temp dump function
        private const val ID_CLIPS_PAGER = "com.instagram.android:id/clips_viewer_view_pager"
        private const val ID_AUTHOR_USERNAME = "com.instagram.android:id/clips_author_username"
        private const val ID_TAB_CLIPS = "com.instagram.android:id/clips_tab"

        // button group
        private const val ID_LIKE_BUTTON = "com.instagram.android:id/like_button"
        private const val ID_COMMENT_BUTTON = "com.instagram.android:id/comment_button"
        private const val ID_DIRECT_SHARE_BUTTON = "com.instagram.android:id/direct_share_button"
        private const val ID_CLIPS_UFI_MORE_BUTTON_COMPONENT = "com.instagram.android:id/clips_ufi_more_button_component"

        // caption extraction
        private const val ID_CAPTION_COMPONENT = "com.instagram.android:id/clips_caption_component"
        private const val DEBOUNCE_MS = 400L

        //notification limits
        private const val LIMIT_CHANNEL_ID = "reels_limit_channel"
        private const val LIMIT_NOTIFICATION_ID = 1001
        
        // Instagram feed
        private const val ID_FEED_TAB = "com.instagram.android:id/feed_tab"
        private const val LAUNCH_REDIRECT_DELAY_MS = 500L // give the UI time to render before clicking

        // isReelsScreen() flickers false for single frames during normal scrolling
        // (23 false readings observed within 86ms in one real session) — only treat
        // it as "actually left Reels" if it stays false for this long.
        private const val SESSION_STOP_DEBOUNCE_MS = 500L

        // Eject verification/retry
        private const val EJECT_VERIFY_DELAY_MS = 300L
        private const val MAX_EJECT_ATTEMPTS = 3
    }

    // Debounce
    private val debounceHandler = Handler(Looper.getMainLooper())
    private var debounceRunnable: Runnable? = null
    private var pendingStopRunnable: Runnable? = null

    // TEMP: UI exploration, remove before production
    private var lastDumpMs = 0L

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository: ReelsRepository by lazy {
        RoomReelsRepository(AppDatabase.getInstance(applicationContext).reelDao(), applicationContext)
    }

    // Interim de-dup heuristic: insert only when the username changes from the
    // last inserted one. Not real debounce (won't handle scrolling back to a
    // previous Reel, rapid re-scrolls, etc.) — a placeholder until a proper
    // "confirmed new Reel" trigger exists.
    private var lastInsertedUsername: String? = null

    //keep track of whether it is in reels session
    private var isReelsSession = false
    private var sessionStartElapsed = 0L
    private var lastForegroundPackage: String? = null


    private var todayTotalAtSessionStart = 0L
    private var cachedDailyLimitMs = Long.MAX_VALUE // safe default until loaded
    private var cachedExtensionMs = 0L
    private var cachedExtensionsUsed = 0
    private var cachedMaxExtensions = 0
    private var hasBumpedThisSession = false
    private var limitStateLoadJob: Job? = null

    private val limitOverlay by lazy { LimitOverlay(this) }


    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        Log.d(TAG, "eventType=${AccessibilityEvent.eventTypeToString(event.eventType)} pkg=${event.packageName}")

        // Our own overlay window fires events too. Without this, showing it would
        // look like the user left Instagram (the non-Instagram branch below).
        if (event.packageName == packageName) return

        // Timing is already committed and the eject is owned by the overlay flow,
        // so ignore everything except the user leaving for another app — then
        // there's nothing left to eject from, so just take the overlay down.
        if (limitOverlay.isShowing) {
            if (event.packageName != INSTAGRAM_PKG &&
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
        if (event.packageName != INSTAGRAM_PKG) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED){
                pendingStopRunnable?.let { debounceHandler.removeCallbacks(it) }
                pendingStopRunnable = null
                stopReelsTiming()
            }
            return // TODO: add more later as we are not only limited to Instagram
        }
        val root = rootInActiveWindow ?: return

        // Detection runs first — dumpNodeTree recycles root, so it must go last
        val (isReels, matchCount, totalSignals) = isReelsScreen(root)
        Log.d(TAG, "isReels=$isReels matchCount=$matchCount totalSignals=$totalSignals")
        updateReelsTiming(isReels)

        if (isReels) {
            val username = extractUsername(root)
            Log.d(TAG, "REELS DETECTED username=$username") // TEMP: verify username extraction, remove before production

            if (username != null && username != lastInsertedUsername) {
                lastInsertedUsername = username
                scheduleExtraction()
            }
        }  else {
            debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
        }

        // TEMP: UI exploration, remove before production
        val now = System.currentTimeMillis()
        if (now - lastDumpMs >= DUMP_INTERVAL_MS) {
            lastDumpMs = now
            dumpNodeTree(root, depth = 0) // recycles root
        } else {
            root.recycle()
        }

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
            "Reels time limit",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /**
     * Resets a short timer every time this is called. Only fires the actual
     * extraction once no new events have come in for DEBOUNCE_MS — i.e. once
     * the screen has "settled" instead of still mid-transition.
     */
    private fun scheduleExtraction() {
        debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
        val runnable = Runnable {
            val freshRoot = rootInActiveWindow ?: return@Runnable

            // Re-check everything against the settled screen — the username
            // that triggered this might have already changed if the user
            // kept scrolling during the 400ms debounce window.
            val (stillReels, _, _) = isReelsScreen(freshRoot)
            if (!stillReels) { freshRoot.recycle(); return@Runnable }

            val username = extractUsername(freshRoot)
            val caption = extractCaption(freshRoot)
            val hashtags = extractHashtags(caption)
            Log.d(TAG, "extracted username=$username caption=$caption hashtags=$hashtags")

            if (username != null) {
                val entry = ReelEntry(
                    username = username,
                    caption = caption,
                    hashtags = hashtags,
                    screenshotPath = null, // filled in after screenshot resolves
                    platform = Platform.INSTAGRAM,
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

    /**
     * Finds the caption text inside clips_caption_component. Per the dump,
     * the container itself has no visible text — the actual string shows up
     * as a contentDescription on a node nested inside it.
     */
    private fun extractCaption(root: AccessibilityNodeInfo): String? {
        val containers = root.findAccessibilityNodeInfosByViewId(ID_CAPTION_COMPONENT)
            .filter { it.isVisibleToUser }
        if (containers.isEmpty()) return null

        return containers.firstNotNullOfOrNull { findFirstContentDescription(it) }
    }

    private fun findFirstContentDescription(node: AccessibilityNodeInfo): String? {
        node.contentDescription?.toString()?.let { if (it.isNotBlank()) return it }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            findFirstContentDescription(child)?.let { return it }
        }
        return null
    }

    private fun extractHashtags(caption: String?): List<String> {
        if (caption.isNullOrBlank()) return emptyList()
        return Regex("#\\w+").findAll(caption).map { it.value }.toList()
    }

    /**
     * Checks the current screen for known Reels signals and returns
     * true if enough of them match (so one Instagram update breaking
     * one signal doesn't break detection entirely).
     */
    private fun isReelsScreen(root: AccessibilityNodeInfo): Triple<Boolean, Int, Int> {
        val signals = listOf(
            hasViewId(root, ID_CLIPS_PAGER),
            hasViewId(root, ID_AUTHOR_USERNAME),
            hasViewId(root, ID_LIKE_BUTTON) &&
            hasViewId(root, ID_COMMENT_BUTTON) &&
            hasViewId(root, ID_DIRECT_SHARE_BUTTON) &&
            hasViewId(root, ID_CLIPS_UFI_MORE_BUTTON_COMPONENT),
            isTabSelected(root, ID_TAB_CLIPS),
            // add more checks here as we nail down more signals,
            // e.g. hasContentDescription(root, "Like"), hasContentDescription(root, "Share reel")
        )

        val matchCount = signals.count { it }
        val threshold = 2 // start low with few signals; raise as we add more
        return Triple(matchCount >= threshold, matchCount, signals.size)
    }

    /**
     * Searches the entire UI tree for an element with a specific view ID
     */
    private fun hasViewId(root: AccessibilityNodeInfo, viewId: String): Boolean {
        return root.findAccessibilityNodeInfosByViewId(viewId)
            .any { it.isVisibleToUser }
    }

    /**
     * Reads the author username text off the clips_author_username node.
     * recycle() is a no-op on API 33+ (pool removed) but still matters on
     * this app's minSdk 26-32 range, so it's called unconditionally.
     */
    private fun extractUsername(root: AccessibilityNodeInfo): String? {
        val nodes = root.findAccessibilityNodeInfosByViewId(ID_AUTHOR_USERNAME)
        try {
            return nodes.firstOrNull { it.isVisibleToUser }
                ?.text?.toString()?.trim()
                ?.takeIf { it.isNotEmpty() }
        } finally {
            nodes.forEach { it.recycle() }
        }
    }

    /**
     * Recursively search for UI elements with specific text content descriptions
     *
     * Can be used to find buttons/icons by text description
     */
    private fun hasContentDescription(root: AccessibilityNodeInfo, text: String): Boolean {
        if (root.contentDescription?.contains(text, ignoreCase = true) == true) return true
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            if (hasContentDescription(child, text)) return true
        }
        return false
    }

    /**
     * Check if the user is on a certain tab
     */
    private fun isTabSelected(root: AccessibilityNodeInfo, tabId: String): Boolean {
        return root.findAccessibilityNodeInfosByViewId(tabId)
            .any { it.isSelected }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        // Otherwise a service disabled mid-countdown leaves the overlay stuck on screen
        limitOverlay.removeNow()
        pendingStopRunnable?.let { debounceHandler.removeCallbacks(it) }
        pendingStopRunnable = null
        stopReelsTiming()
        super.onDestroy()
        serviceScope.cancel()
    }

    // TEMP: UI exploration, remove before production
    private fun dumpNodeTree(node: AccessibilityNodeInfo?, depth: Int) {
        if (node == null) return

        val indent = "  ".repeat(depth)
        val cls = node.className ?: ""
        val id = node.viewIdResourceName ?: ""
        val text = node.text ?: ""
        val desc = node.contentDescription ?: ""

        Log.d(TAG, "$indent[$depth] cls=$cls id=$id text=$text desc=$desc")

        for (i in 0 until node.childCount) {
            dumpNodeTree(node.getChild(i), depth + 1)
        }

        node.recycle()
    }

    //Lazy so applicationContext access happens after service creation
    private val screenTimeStore: ScreenTimeStore by lazy {
        ScreenTimeStore(applicationContext)
    }

    private fun updateReelsTiming(isReels: Boolean) {
        if (isReels) {
            // We're on Reels (still, or again after a brief flicker) — cancel any
            // debounced stop so a single-frame false reading doesn't tear the
            // session down and reset the limit state mid-watch.
            pendingStopRunnable?.let { debounceHandler.removeCallbacks(it) }
            pendingStopRunnable = null

            if (!isReelsSession) {
                isReelsSession = true
                sessionStartElapsed = SystemClock.elapsedRealtime()
                hasBumpedThisSession = false
                loadSessionLimitState()
            } else {
                checkLimitDuringSession()
            }
        } else if (isReelsSession && pendingStopRunnable == null) {
            val runnable = Runnable {
                pendingStopRunnable = null
                stopReelsTiming()
            }
            pendingStopRunnable = runnable
            debounceHandler.postDelayed(runnable, SESSION_STOP_DEBOUNCE_MS)
        }
    }

    /**
     * Loads the values checkLimitDuringSession() compares against for this
     * session. Cancels any load still in flight from a session that just
     * ended (e.g. a rapid stop/restart from detection flicker) so a stale
     * result can't land after this one and overwrite it with old data.
     */
    private fun loadSessionLimitState() {
        limitStateLoadJob?.cancel()
        limitStateLoadJob = serviceScope.launch {
            val total = screenTimeStore.getTodayTotal()
            val limit = screenTimeStore.getDailyLimit() * 60_000L
            val extension = screenTimeStore.getExtensionToday()
            val extensionsUsed = screenTimeStore.getExtensionsUsedToday()
            val maxExtensions = screenTimeStore.getStrictness().maxExtensionsPerDay
            todayTotalAtSessionStart = total
            cachedMaxExtensions = maxExtensions
            cachedExtensionMs = extension
            cachedExtensionsUsed = extensionsUsed
            cachedDailyLimitMs = limit // written last — it's what arms checkLimitDuringSession()
        }
    }

    /** The daily limit plus any extra time granted today. */
    private fun effectiveLimitMs(): Long =
        if (cachedDailyLimitMs == Long.MAX_VALUE) Long.MAX_VALUE else cachedDailyLimitMs + cachedExtensionMs

    private fun checkLimitDuringSession() {
        if (hasBumpedThisSession) return // don't spam performGlobalAction every event
        val elapsedInSession = SystemClock.elapsedRealtime() - sessionStartElapsed
        val projectedTotal = todayTotalAtSessionStart + elapsedInSession
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
        val spec = LimitOverlaySpec.forBump(
            watchedMs = watchedMs,
            limitMs = cachedDailyLimitMs,
            extraMs = cachedExtensionMs,
            extensionsUsedToday = cachedExtensionsUsed,
            maxExtensionsPerDay = cachedMaxExtensions,
            alreadyOverAtSessionStart = todayTotalAtSessionStart >= effectiveLimitMs(),
        )
        Log.d(TAG, "Daily limit exceeded mid-session — showing ${spec.variant} overlay before eject")

        // Commit watch time up to now so the countdown itself isn't counted as Reels time
        pendingStopRunnable?.let { debounceHandler.removeCallbacks(it) }
        pendingStopRunnable = null
        debounceRunnable?.let { debounceHandler.removeCallbacks(it) }
        stopReelsTiming()

        limitOverlay.show(
            spec,
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
            val stillInReels = root != null && isReelsScreen(root).first
            root?.recycle()

            if (!stillInReels) {
                stopReelsTiming()
                limitOverlay.dismiss()
            } else if (attempt < MAX_EJECT_ATTEMPTS) {
                Log.w(TAG, "Eject attempt $attempt still in Reels — retrying")
                attemptEject(attempt + 1)
            } else {
                Log.w(TAG, "Eject failed after $MAX_EJECT_ATTEMPTS attempts, giving up for this session")
                stopReelsTiming()
                limitOverlay.dismiss()
            }
        }, EJECT_VERIFY_DELAY_MS)
    }

    private fun stopReelsTiming(){
        if (!isReelsSession) return
        isReelsSession = false
        limitStateLoadJob?.cancel()
        val delta = SystemClock.elapsedRealtime() - sessionStartElapsed
        serviceScope.launch {
            screenTimeStore.addElapsedTime(delta)
            checkDailyLimit()
        }
    }

    private suspend fun checkDailyLimit(){
        // Once a day — the overlay already covers every bump, so repeating the
        // notification (and its sound) on each Reels visit is just noise
        if (screenTimeStore.isNotifiedToday()) return
        val totalMs = screenTimeStore.getTodayTotal()
        val limitMs = screenTimeStore.getDailyLimit() * 60_000L
        if (totalMs >= limitMs) {
            showLimitNotification()
            screenTimeStore.setNotifiedToday(true)
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