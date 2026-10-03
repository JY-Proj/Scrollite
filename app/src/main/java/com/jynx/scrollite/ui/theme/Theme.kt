package com.jynx.scrollite.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

private val ScrolliteShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    // Banners
    medium = RoundedCornerShape(14.dp),
    // Cards
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Spacing and outline sizes shared across screens. */
object ScrolliteDimens {
    val ScreenPadding = 16.dp
    val SectionGap = 14.dp
    val CardPadding = 16.dp
    val CardOutline = 1.5.dp
    val EmphasisOutline = 2.dp
}

/**
 * Brand colors rather than the wallpaper-based dynamic colors, so Scrollite
 * always looks like Scrollite. [darkTheme] comes from the user's appearance
 * setting (see ThemeMode).
 */
@Composable
fun ScrolliteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = palette()
    val colors = if (darkTheme) darkScrolliteColors(palette) else lightScrolliteColors(palette)
    CompositionLocalProvider(LocalScrolliteColors provides colors) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkScheme(palette) else lightScheme(palette),
            typography = Typography,
            shapes = ScrolliteShapes,
            content = content,
        )
    }
}

/** Access to the roles in [ScrolliteColors], alongside MaterialTheme's. */
object ScrolliteTheme {
    val colors: ScrolliteColors
        @Composable
        @ReadOnlyComposable
        get() = LocalScrolliteColors.current
}
