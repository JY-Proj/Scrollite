package com.jynx.scrollite.ui.limit

/**
 * What the limit overlay should say and how long it should count down.
 * Kept free of Android types so the decision logic is unit-testable.
 */
data class LimitOverlaySpec(
    /** The app's own name for what the user is being moved out of, e.g. "Reels". */
    val shortFormLabel: String,
    val variant: Variant,
    val watchedMinutes: Long,
    val limitMinutes: Long,
    val extraMinutes: Long,
    val countdownSeconds: Int,
    val extensionsRemaining: Int,
) {
    val canExtend: Boolean get() = extensionsRemaining > 0

    enum class Variant {
        /** The limit was crossed during this session — first time the user sees it. */
        LIMIT_REACHED,

        /** The extra time from an extension ran out during this session. */
        EXTRA_TIME_UP,

        /** The user opened Reels when already over the limit — shorter, less naggy. */
        STILL_OVER_LIMIT,
    }

    companion object {
        const val FULL_COUNTDOWN_SECONDS = 5
        const val SHORT_COUNTDOWN_SECONDS = 2

        /**
         * @param limitMs the user's daily limit, without any extension
         * @param extraMs extra time granted today on top of [limitMs]
         * @param alreadyOverAtSessionStart whether the user was already past
         *   limit + extra when this Reels session began
         */
        fun forBump(
            shortFormLabel: String,
            watchedMs: Long,
            limitMs: Long,
            extraMs: Long,
            extensionsUsedToday: Int,
            maxExtensionsPerDay: Int,
            alreadyOverAtSessionStart: Boolean,
        ): LimitOverlaySpec {
            val variant = when {
                alreadyOverAtSessionStart -> Variant.STILL_OVER_LIMIT
                extraMs > 0 -> Variant.EXTRA_TIME_UP
                else -> Variant.LIMIT_REACHED
            }
            val remaining = ExtensionPolicy.remaining(extensionsUsedToday, maxExtensionsPerDay)
            return LimitOverlaySpec(
                shortFormLabel = shortFormLabel,
                variant = variant,
                watchedMinutes = watchedMs / 60_000L,
                limitMinutes = limitMs / 60_000L,
                extraMinutes = extraMs / 60_000L,
                // The short countdown is only for a repeat visit with nothing left to
                // decide — 2s is too little time to notice and tap "Need more time?"
                countdownSeconds =
                    if (variant == Variant.STILL_OVER_LIMIT && remaining == 0) SHORT_COUNTDOWN_SECONDS
                    else FULL_COUNTDOWN_SECONDS,
                extensionsRemaining = remaining,
            )
        }
    }
}
