package app.seb3thehacker.gearslip.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.seb3thehacker.gearslip.car.theme.withTintedSurfaces

/**
 * Material You: the palette comes from the wallpaper (minSdk 35, so dynamic color always exists),
 * with the same accent tint over the surfaces as the car screen, so the two look like one app.
 */
@Composable
fun GearslipTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = (if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)).withTintedSurfaces(dark)
    MaterialTheme(colorScheme = scheme, content = content)
}
