package app.seb3thehacker.gearslip.car.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp

/**
 * The phone's Material You colours, with its accent washed over the surfaces. Material You's own
 * surfaces are nearly neutral, which reads as flat grey on a car screen; tinting them with the
 * phone's primary colour (purple on a purple phone) gives the whole UI the phone's hue, the way
 * Material 3's surface tint does, and it changes with the wallpaper like everything else.
 * Higher containers take more of the tint, so cards still stand off the background.
 */
fun ColorScheme.withTintedSurfaces(dark: Boolean, style: DarkStyle = DarkStyle.GREY): ColorScheme {
    if (dark && style != DarkStyle.GREY) return withDarkStyle(style)
    val base = if (dark) 0.03f else 0.02f
    fun tint(own: Color, step: Int) = primary.copy(alpha = base + step * (if (dark) 0.015f else 0.01f)).compositeOver(own)
    return copy(
        background = tint(background, 0),
        surface = tint(surface, 0),
        surfaceDim = tint(surfaceDim, 0),
        surfaceContainerLowest = tint(surfaceContainerLowest, 0),
        surfaceContainerLow = tint(surfaceContainerLow, 1),
        surfaceContainer = tint(surfaceContainer, 2),
        surfaceContainerHigh = tint(surfaceContainerHigh, 3),
        surfaceContainerHighest = tint(surfaceContainerHighest, 4),
        surfaceBright = tint(surfaceBright, 4),
        surfaceVariant = tint(surfaceVariant, 3),
    )
}

/**
 * How dark the car's dark mode goes. [GREY] is Material You's own dark, with the tint above;
 * the others are darker, and the two colour ones take their hue from the phone's accent.
 */
enum class DarkStyle { GREY, BLACK, DEEP_COLOUR, BLACK_COLOUR }

/**
 * The surfaces rebuilt at [style]'s brightness, from the background up to the highest container.
 * The levels are 0-255 greys; each surface keeps Material You's own slight hue at that level.
 */
private fun ColorScheme.withDarkStyle(style: DarkStyle): ColorScheme {
    val levels = when (style) {
        DarkStyle.BLACK -> listOf(0f, 12f, 17f, 25f, 34f)
        DarkStyle.DEEP_COLOUR -> listOf(11f, 19f, 24f, 32f, 41f)
        else -> listOf(0f, 17f, 22f, 30f, 39f)
    }
    val colour = style != DarkStyle.BLACK
    // The accent's saturated container colour, not the pastel accent itself: tinting with the
    // pastel only makes a surface lighter, where this one gives it colour at the same brightness.
    fun at(own: Color, step: Int): Color {
        val level = levels[step] / 255f
        if (level == 0f) return Color.Black
        val neutral = own.scaledTo(level)
        return if (colour) {
            lerp(neutral, primaryContainer.scaledTo(level), (0.6f + 0.08f * step).coerceAtMost(0.95f))
        } else {
            primary.copy(alpha = 0.02f + 0.015f * step).compositeOver(neutral)
        }
    }
    return copy(
        // Grey text that read fine on grey looks dim on black, so it moves a little towards white.
        onSurfaceVariant = lerp(onSurfaceVariant, onSurface, 0.3f),
        background = at(background, 0),
        surface = at(surface, 0),
        surfaceDim = at(surfaceDim, 0),
        surfaceContainerLowest = at(surfaceContainerLowest, 0),
        surfaceContainerLow = at(surfaceContainerLow, 1),
        surfaceContainer = at(surfaceContainer, 2),
        surfaceContainerHigh = at(surfaceContainerHigh, 3),
        surfaceContainerHighest = at(surfaceContainerHighest, 4),
        surfaceBright = at(surfaceBright, 4),
        surfaceVariant = at(surfaceVariant, 3),
    )
}

/** This colour's hue at an average brightness of [level], 0 to 1. */
private fun Color.scaledTo(level: Float): Color {
    val mean = (red + green + blue) / 3f
    if (mean <= 0f) return Color(level, level, level)
    val k = level / mean
    return Color((red * k).coerceAtMost(1f), (green * k).coerceAtMost(1f), (blue * k).coerceAtMost(1f))
}
