package com.jynx.scrollite.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.jynx.scrollite.R

/**
 * The brand palette. The hex values live in res/values/colors.xml, shared
 * with the XML limit overlay; this only reads them, so the two can't drift.
 * To retune a color, change it there. To change where a color is used,
 * change the role mappings below.
 */
@Immutable
data class Palette(
    val grayCyan: Color,
    val teal: Color,
    val green: Color,
    val yellow: Color,
    val turquoise: Color,
    val chartTeal: Color,
    val ink: Color,
    val white: Color,
    val gray50: Color,
    val gray100: Color,
    val gray200: Color,
    val gray300: Color,
    val gray600: Color,
    val gray800: Color,
    val gray900: Color,
)

@Composable
internal fun palette() = Palette(
    grayCyan = colorResource(R.color.brand_gray_cyan),
    teal = colorResource(R.color.brand_teal),
    green = colorResource(R.color.brand_green),
    yellow = colorResource(R.color.brand_yellow),
    turquoise = colorResource(R.color.brand_turquoise),
    chartTeal = colorResource(R.color.brand_chart_teal),
    ink = colorResource(R.color.neutral_ink),
    white = colorResource(R.color.neutral_white),
    gray50 = colorResource(R.color.neutral_gray_50),
    gray100 = colorResource(R.color.neutral_gray_100),
    gray200 = colorResource(R.color.neutral_gray_200),
    gray300 = colorResource(R.color.neutral_gray_300),
    gray600 = colorResource(R.color.neutral_gray_600),
    gray800 = colorResource(R.color.neutral_gray_800),
    gray900 = colorResource(R.color.neutral_gray_900),
)

/**
 * Chart colors. Slots are indexed by an app's or section's fixed order (never
 * its rank), so a color always means the same thing. Pale slots get an
 * outline where they'd otherwise vanish into the background.
 */
@Immutable
data class ChartColors(
    val slots: List<Color>,
    /** The neutral "Other" bucket. */
    val other: Color,
    /** Outline for pale slots (every slot after the first); Unspecified = none. */
    val paleOutline: Color,
    val grid: Color,
    val baseline: Color,
    val limit: Color,
) {
    fun forSlot(slot: Int?): Color = if (slot == null) other else slots[slot % slots.size]

    fun outlineFor(slot: Int?): Color? =
        if (slot != null && slot % slots.size != 0 && paleOutline != Color.Unspecified) paleOutline else null
}

/** Roles Material's ColorScheme has no slot for. */
@Immutable
data class ScrolliteColors(
    val heroFill: Color,
    val heroTrack: Color,
    val heroOutline: Color,
    val cardOutline: Color,
    val chipOutline: Color,
    val headsUpOutline: Color,
    val headsUpIcon: Color,
    val progress: Color,
    val progressTrack: Color,
    val thumbnailOutline: Color,
    val divider: Color,
    val chart: ChartColors,
)

internal val LocalScrolliteColors = staticCompositionLocalOf<ScrolliteColors> {
    error("ScrolliteColors not provided; wrap the UI in ScrolliteTheme")
}

/** Minimal white UI; the palette shows up as outlines and small accents. */
internal fun lightScrolliteColors(p: Palette) = ScrolliteColors(
    heroFill = p.white,
    heroTrack = p.gray50,
    heroOutline = p.green,
    cardOutline = p.gray100,
    chipOutline = p.gray200,
    headsUpOutline = p.yellow,
    headsUpIcon = p.ink,
    progress = p.turquoise,
    progressTrack = p.gray50,
    thumbnailOutline = p.green,
    divider = p.gray100,
    chart = ChartColors(
        slots = listOf(p.chartTeal, p.teal, p.yellow, p.green),
        other = p.gray300,
        paleOutline = p.turquoise,
        grid = p.gray100,
        baseline = p.gray200,
        limit = p.gray600,
    ),
)

/** Near-black UI; the pastels read clearly as outlines here, so they lead. */
internal fun darkScrolliteColors(p: Palette) = ScrolliteColors(
    heroFill = p.ink,
    heroTrack = p.gray900,
    heroOutline = p.green,
    cardOutline = p.teal,
    chipOutline = p.teal,
    headsUpOutline = p.yellow,
    headsUpIcon = p.yellow,
    progress = p.teal,
    progressTrack = p.gray900,
    thumbnailOutline = p.teal,
    divider = p.gray900,
    chart = ChartColors(
        slots = listOf(p.chartTeal, p.teal, p.yellow, p.green),
        other = p.gray600,
        paleOutline = Color.Unspecified,
        grid = p.gray900,
        baseline = p.gray800,
        limit = p.gray300,
    ),
)

internal fun lightScheme(p: Palette): ColorScheme = lightColorScheme(
    primary = p.turquoise,
    onPrimary = p.white,
    primaryContainer = p.teal,
    onPrimaryContainer = p.ink,
    secondary = p.turquoise,
    onSecondary = p.white,
    // Selected chips, tabs and segmented buttons
    secondaryContainer = p.teal,
    onSecondaryContainer = p.ink,
    tertiary = p.turquoise,
    onTertiary = p.white,
    // Soft gray page behind white cards
    background = p.gray50,
    onBackground = p.ink,
    surface = p.white,
    onSurface = p.ink,
    surfaceVariant = p.gray50,
    onSurfaceVariant = p.gray600,
    surfaceContainerLowest = p.white,
    surfaceContainerLow = p.white,
    surfaceContainer = p.white,
    surfaceContainerHigh = p.white,
    surfaceContainerHighest = p.gray50,
    outline = p.gray200,
    outlineVariant = p.gray100,
)

internal fun darkScheme(p: Palette): ColorScheme = darkColorScheme(
    primary = p.teal,
    onPrimary = p.ink,
    primaryContainer = p.turquoise,
    onPrimaryContainer = p.white,
    secondary = p.teal,
    onSecondary = p.ink,
    secondaryContainer = p.teal,
    onSecondaryContainer = p.ink,
    tertiary = p.teal,
    onTertiary = p.ink,
    background = p.ink,
    onBackground = p.white,
    surface = p.ink,
    onSurface = p.white,
    surfaceVariant = p.gray900,
    onSurfaceVariant = p.gray300,
    surfaceContainerLowest = p.ink,
    surfaceContainerLow = p.ink,
    surfaceContainer = p.ink,
    surfaceContainerHigh = p.ink,
    surfaceContainerHighest = p.gray900,
    outline = p.teal,
    outlineVariant = p.gray900,
)
