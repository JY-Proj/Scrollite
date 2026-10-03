package com.jynx.scrollite.ui.limit

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.jynx.scrollite.R
import com.jynx.scrollite.data.ThemeMode

/**
 * Full-screen "time's up" screen drawn over the watched app before we eject
 * the user, so the bump doesn't feel abrupt. Uses TYPE_ACCESSIBILITY_OVERLAY,
 * which an accessibility service can add without SYSTEM_ALERT_WINDOW.
 *
 * Not focusable (so rootInActiveWindow stays on the watched app, which the
 * eject verification relies on) but does consume touches, so the user can't
 * keep scrolling underneath it. Must be used from the main thread.
 */
class LimitOverlay(private val service: AccessibilityService) {

    private companion object {
        const val TAG = "LimitOverlay"
        const val FADE_MS = 200L
        const val TICK_MS = 1000L
    }

    private val windowManager = service.getSystemService(WindowManager::class.java)
    private val audioManager = service.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var view: View? = null
    private var views: Views? = null
    private var tickRunnable: Runnable? = null
    private var onLeave: (() -> Unit)? = null
    private var onExtend: ((minutes: Int) -> Unit)? = null
    private var focusRequest: AudioFocusRequest? = null

    val isShowing: Boolean get() = view != null

    private class Views(root: View) {
        val mainGroup: View = root.findViewById(R.id.limit_main_group)
        val title: TextView = root.findViewById(R.id.limit_title)
        val body: TextView = root.findViewById(R.id.limit_body)
        val cardValue: TextView = root.findViewById(R.id.limit_card_value)
        val cardLimit: TextView = root.findViewById(R.id.limit_card_limit)
        val cardProgress: ProgressBar = root.findViewById(R.id.limit_card_progress)
        val leaveButton: TextView = root.findViewById(R.id.limit_button)
        val extendLink: TextView = root.findViewById(R.id.limit_extend_link)
        val extendGroup: View = root.findViewById(R.id.limit_extend_group)
        val extendTitle: TextView = root.findViewById(R.id.limit_extend_title)
        val extendBody: TextView = root.findViewById(R.id.limit_extend_body)
        val extendStatus: TextView = root.findViewById(R.id.limit_extend_status)
        val extendShort: TextView = root.findViewById(R.id.limit_extend_short)
        val extendLong: TextView = root.findViewById(R.id.limit_extend_long)
        val extendCancel: TextView = root.findViewById(R.id.limit_extend_cancel)
    }

    /**
     * Shows the overlay and starts the countdown. Exactly one of the callbacks
     * fires: [onLeave] when the countdown ends or the user taps through, or
     * [onExtend] when they pick extra time. The overlay stays up after either
     * until [dismiss], so the caller can eject or save the extension behind it.
     */
    @SuppressLint("InflateParams") // A window root has no parent; WindowManager supplies the layout params
    fun show(
        spec: LimitOverlaySpec,
        themeMode: ThemeMode,
        onLeave: () -> Unit,
        onExtend: (minutes: Int) -> Unit,
    ) {
        if (isShowing) return

        val root = LayoutInflater.from(themedContext(themeMode)).inflate(R.layout.overlay_limit, null)
        val v = Views(root)

        when (spec.variant) {
            LimitOverlaySpec.Variant.LIMIT_REACHED -> {
                v.title.setText(R.string.limit_overlay_title_reached)
                v.body.setText(R.string.limit_overlay_body_reached)
            }
            LimitOverlaySpec.Variant.EXTRA_TIME_UP -> {
                v.title.setText(R.string.limit_overlay_title_extra_up)
                v.body.text = service.getString(R.string.limit_overlay_body_extra_up, spec.extraMinutes)
            }
            LimitOverlaySpec.Variant.STILL_OVER_LIMIT -> {
                v.title.setText(R.string.limit_overlay_title_still_over)
                v.body.setText(R.string.limit_overlay_body_still_over)
            }
        }
        bindUsageCard(v, spec)
        // Announced by TalkBack when the overlay appears
        ViewCompat.setAccessibilityPaneTitle(root, v.title.text)

        this.onLeave = onLeave
        this.onExtend = onExtend
        v.extendCancel.text = service.getString(R.string.limit_overlay_leave, spec.shortFormLabel)
        v.leaveButton.setOnClickListener { leave() }
        v.extendCancel.setOnClickListener { leave() }
        if (spec.canExtend) {
            v.extendLink.visibility = View.VISIBLE
            v.extendLink.setOnClickListener { startExtendFlow() }
            v.extendBody.text = service.resources.getQuantityString(
                R.plurals.limit_overlay_extend_body, spec.extensionsRemaining, spec.extensionsRemaining
            )
        }
        v.extendShort.text = service.getString(R.string.limit_overlay_extend_option, ExtensionPolicy.SHORT_OPTION_MINUTES)
        v.extendLong.text = service.getString(R.string.limit_overlay_extend_option, ExtensionPolicy.LONG_OPTION_MINUTES)
        v.extendShort.setOnClickListener { extend(ExtensionPolicy.SHORT_OPTION_MINUTES) }
        v.extendLong.setOnClickListener { extend(ExtensionPolicy.LONG_OPTION_MINUTES) }

        try {
            windowManager.addView(root, layoutParams())
        } catch (e: Exception) {
            // Never leave the user stuck in Reels because the overlay couldn't be drawn
            Log.w(TAG, "Failed to add overlay, ejecting directly: ${e.message}")
            this.onLeave = null
            this.onExtend = null
            onLeave()
            return
        }
        view = root
        views = v
        root.alpha = 0f
        root.animate().alpha(1f).setDuration(FADE_MS).start()

        requestAudioFocus()
        runTicker(spec.countdownSeconds,
            onTick = { v.leaveButton.text = service.getString(R.string.limit_overlay_button_countdown, spec.shortFormLabel, it) },
            onFinish = { leave() },
        )
    }

    /**
     * The service's own context follows the phone's light/dark setting; when
     * the user picked one in Scrollite, override it so the overlay's
     * values / values-night colors match the app.
     */
    private fun themedContext(mode: ThemeMode): Context {
        val nightBits = when (mode) {
            ThemeMode.SYSTEM -> return service
            ThemeMode.LIGHT -> Configuration.UI_MODE_NIGHT_NO
            ThemeMode.DARK -> Configuration.UI_MODE_NIGHT_YES
        }
        val config = Configuration(service.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightBits
        }
        return service.createConfigurationContext(config)
    }

    private fun bindUsageCard(v: Views, spec: LimitOverlaySpec) {
        v.cardValue.text = service.getString(R.string.limit_overlay_card_value, spec.watchedMinutes)
        v.cardLimit.text =
            if (spec.extraMinutes > 0) {
                service.getString(R.string.limit_overlay_card_limit_with_extra, spec.limitMinutes, spec.extraMinutes)
            } else {
                service.getString(R.string.limit_overlay_card_limit, spec.limitMinutes)
            }
        val allowedMinutes = (spec.limitMinutes + spec.extraMinutes).coerceAtLeast(1)
        v.cardProgress.max = allowedMinutes.toInt()
        v.cardProgress.progress = spec.watchedMinutes.coerceAtMost(allowedMinutes).toInt()
    }

    /** Fades the overlay out and cancels a countdown still in progress. */
    fun dismiss() {
        val root = view ?: return
        cleanUp()
        root.animate().alpha(0f).setDuration(FADE_MS).withEndAction { removeView(root) }.start()
    }

    /** Removes the overlay immediately — for service teardown, where an animation may never finish. */
    fun removeNow() {
        val root = view ?: return
        cleanUp()
        root.animate().cancel()
        removeView(root)
    }

    /**
     * Pauses the leave countdown and swaps to the extension screen, where the
     * options stay disabled for [ExtensionPolicy.WAIT_SECONDS] — extending
     * should take a moment of intent, not be a reflexive snooze tap.
     */
    private fun startExtendFlow() {
        val v = views ?: return
        cancelTicker()
        v.mainGroup.visibility = View.GONE
        v.extendGroup.visibility = View.VISIBLE
        view?.let { ViewCompat.setAccessibilityPaneTitle(it, v.extendTitle.text) }

        runTicker(ExtensionPolicy.WAIT_SECONDS,
            onTick = { v.extendStatus.text = service.getString(R.string.limit_overlay_extend_wait, it) },
            onFinish = {
                v.extendStatus.setText(R.string.limit_overlay_extend_ready)
                v.extendShort.isEnabled = true
                v.extendLong.isEnabled = true
            },
        )
    }

    private fun leave() {
        val v = views ?: return
        cancelTicker()
        val callback = onLeave ?: return
        onLeave = null
        onExtend = null
        // Back to the main screen (if leaving from the extension screen) so the user sees what's happening
        v.extendGroup.visibility = View.GONE
        v.mainGroup.visibility = View.VISIBLE
        v.extendLink.visibility = View.GONE
        v.leaveButton.setText(R.string.limit_overlay_button_leaving)
        v.leaveButton.isEnabled = false
        callback()
    }

    private fun extend(minutes: Int) {
        val v = views ?: return
        val callback = onExtend ?: return
        onLeave = null
        onExtend = null
        v.extendShort.isEnabled = false
        v.extendLong.isEnabled = false
        v.extendCancel.isEnabled = false
        callback(minutes)
    }

    /** Calls [onTick] with seconds remaining (n..1), one second apart, then [onFinish]. */
    private fun runTicker(seconds: Int, onTick: (Int) -> Unit, onFinish: () -> Unit) {
        cancelTicker()
        var remaining = seconds
        val tick = object : Runnable {
            override fun run() {
                if (remaining <= 0) {
                    tickRunnable = null
                    onFinish()
                    return
                }
                onTick(remaining)
                remaining--
                handler.postDelayed(this, TICK_MS)
            }
        }
        tickRunnable = tick
        tick.run()
    }

    private fun cancelTicker() {
        tickRunnable?.let { handler.removeCallbacks(it) }
        tickRunnable = null
    }

    private fun cleanUp() {
        view = null
        views = null
        cancelTicker()
        onLeave = null
        onExtend = null
        abandonAudioFocus()
    }

    private fun removeView(root: View) {
        try {
            windowManager.removeView(root)
        } catch (e: IllegalArgumentException) {
            // Already detached (e.g. the service was torn down mid-fade)
        }
    }

    /**
     * The overlay hides the video but not its sound. Taking transient audio
     * focus makes well-behaved players (Instagram included) pause.
     */
    private fun requestAudioFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        audioManager.requestAudioFocus(request)
        focusRequest = request
    }

    private fun abandonAudioFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        // Cover the status/nav bars and cutout too, so no Reel peeks out around the edges
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            fitInsetsTypes = 0
        }
        // Frosted look over the Reel; without blur support the translucent scrim alone still works
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && windowManager.isCrossWindowBlurEnabled) {
            flags = flags or WindowManager.LayoutParams.FLAG_BLUR_BEHIND
            blurBehindRadius = service.resources.getDimensionPixelSize(R.dimen.limit_overlay_blur_radius)
        }
    }
}
