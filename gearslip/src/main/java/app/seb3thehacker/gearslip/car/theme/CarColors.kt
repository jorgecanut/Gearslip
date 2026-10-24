package app.seb3thehacker.gearslip.car.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver

/**
 * The phone's Material You colours, with its accent washed over the surfaces. Material You's own
 * surfaces are nearly neutral, which reads as flat grey on a car screen; tinting them with the
 * phone's primary colour (purple on a purple phone) gives the whole UI the phone's hue, the way
 * Material 3's surface tint does, and it changes with the wallpaper like everything else.
 * Higher containers take more of the tint, so cards still stand off the background.
 */
fun ColorScheme.withTintedSurfaces(dark: Boolean): ColorScheme {
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
