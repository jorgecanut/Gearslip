package app.seb3thehacker.gearslip.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.seb3thehacker.gearslip.car.AppTheme
import app.seb3thehacker.gearslip.car.CarApps
import app.seb3thehacker.gearslip.car.CarSettings
import app.seb3thehacker.gearslip.car.NavBarMode
import app.seb3thehacker.gearslip.car.NightMode
import app.seb3thehacker.gearslip.car.Units
import app.seb3thehacker.gearslip.car.theme.GsThemes

/**
 * The car's settings on the phone's home screen, one collapsible card per group. They are the
 * same settings as the car's own Settings app, so a change in either shows in both; the phone is
 * just the easier place to make them when parked.
 */
@Composable
internal fun HomeCarSettings(modifier: Modifier = Modifier) {
    val scale by CarSettings.scale.collectAsState()
    val open by CarSettings.openOnConnect.collectAsState()
    val night by CarSettings.nightMode.collectAsState()
    val units by CarSettings.units.collectAsState()
    val navBar by CarSettings.navBarMode.collectAsState()
    val pipeAudio by CarSettings.pipeAudio.collectAsState()
    val speakDirections by CarSettings.speakDirections.collectAsState()
    val systemSpeech by CarSettings.systemSpeech.collectAsState()
    val bluetoothWarning by CarSettings.bluetoothWarning.collectAsState()
    val locationWarning by CarSettings.locationWarning.collectAsState()
    val autoplay by CarSettings.autoplay.collectAsState()
    val playerTitle by CarSettings.playerTitle.collectAsState()
    val playerLeft by CarSettings.playerLeft.collectAsState()
    val theme by CarSettings.appTheme.collectAsState()
    val uiTheme by CarSettings.uiTheme.collectAsState()

    val openChoices = listOf(CarSettings.HOME to "Home") + CarApps.all.filter { it.id != "settings" }.map { it.id to it.label }
    val sizeChoices = listOf(1f to "Normal", 1.25f to "Large", 1.5f to "Extra large")
    val themeChoices = listOf(AppTheme.PHONE to "Follow phone", AppTheme.LIGHT to "Light", AppTheme.DARK to "Dark")
    val lookChoices = GsThemes.builtIn.map { it.id to it.name }
    val nightChoices = listOf(NightMode.AUTO to "Auto", NightMode.DAY to "Day", NightMode.NIGHT to "Night")
    val unitChoices = listOf(Units.AUTO to "Auto", Units.IMPERIAL to "Imperial", Units.METRIC to "Metric")
    val navBarChoices = listOf(
        NavBarMode.SHOW to "Always show",
        NavBarMode.HIDE_NAVIGATING to "Hide while navigating",
        NavBarMode.HIDE to "Hide until tapped",
    )

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Car",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp),
        )
        Group(
            "Screen",
            summary = listOf(openChoices.label(open), sizeChoices.label(scale), themeChoices.label(theme)),
        ) {
            Choice("Open on connect", openChoices, open, CarSettings::setOpenOnConnect)
            SettingsDivider()
            Choice("Size", sizeChoices, scale, CarSettings::setScale)
            SettingsDivider()
            Choice("App theme", themeChoices, theme, CarSettings::setAppTheme)
            SettingsDivider()
            Choice("Buttons", lookChoices, uiTheme, CarSettings::setUiTheme)
            SettingsDivider()
            Choice("Map day/night", nightChoices, night, CarSettings::setNightMode)
            SettingsDivider()
            Choice("Units", unitChoices, units, CarSettings::setUnits)
        }
        Group(
            "Nav bar and player",
            summary = listOf(navBarChoices.label(navBar), "Player on the ${if (playerLeft) "left" else "right"}"),
        ) {
            Choice("Nav bar", navBarChoices, navBar, CarSettings::setNavBarMode)
            SettingsDivider()
            Choice("Player in the nav bar", listOf(false to "Skip buttons", true to "Song title"), playerTitle, CarSettings::setPlayerTitle)
            SettingsDivider()
            Choice("Player beside the map", listOf(false to "Right", true to "Left"), playerLeft, CarSettings::setPlayerLeft)
            SettingsDivider()
            Toggle("Autoplay on connect", autoplay, CarSettings::setAutoplay)
        }
        Group(
            "Sound and voice",
            summary = listOf(if (pipeAudio) "USB audio" else "Bluetooth audio", "Directions ${if (speakDirections) "on" else "off"}"),
        ) {
            Choice("Car audio", listOf(false to "Bluetooth", true to "USB"), pipeAudio, CarSettings::setPipeAudio)
            SettingsDivider()
            Toggle(
                "Spoken directions",
                speakDirections,
                CarSettings::setSpeakDirections,
                note = "Turn it off if your map app's own voice already plays in the car.",
            )
            SettingsDivider()
            Toggle(
                "Use the phone's speech recognition",
                systemSpeech,
                CarSettings::setSystemSpeech,
                note = "On most phones that's Google's, which may send your voice to Google.",
            )
        }
        Group(
            "Warnings",
            summary = listOf(
                "Bluetooth ${if (bluetoothWarning && !pipeAudio) "on" else "off"}",
                "Location ${if (locationWarning) "on" else "off"}",
            ),
        ) {
            if (!pipeAudio) {
                Toggle("Warn when Bluetooth isn't connected", bluetoothWarning, CarSettings::setBluetoothWarning)
                SettingsDivider()
            }
            Toggle("Warn when location is off", locationWarning, CarSettings::setLocationWarning)
        }
    }
}

private fun <T> List<Pair<T, String>>.label(value: T): String = firstOrNull { it.first == value }?.second.orEmpty()

/** One group: its name and a one-line summary, opening in place to the settings themselves. */
@Composable
private fun Group(title: String, summary: List<String>, content: @Composable () -> Unit) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    SettingsCard {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (!open) {
                    Text(
                        summary.filter { it.isNotEmpty() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = if (open) "Close" else "Open", modifier = Modifier.rotate(turn))
        }
        AnimatedVisibility(open) {
            Column {
                SettingsDivider()
                content()
            }
        }
    }
}

/** A setting with a few named choices: its current one shown, the rest a tap away. */
@Composable
private fun <T> Choice(title: String, choices: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    SettingsRow(
        title,
        Modifier.clickable { menu = true },
        trailing = {
            // The menu hangs from the value it changes, at the row's end.
            Box {
                Text(choices.label(selected), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    choices.forEach { (value, label) ->
                        DropdownMenuItem(
                            text = { Text(label, fontWeight = if (value == selected) FontWeight.Bold else FontWeight.Normal) },
                            onClick = {
                                onSelect(value)
                                menu = false
                            },
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun Toggle(title: String, on: Boolean, onChange: (Boolean) -> Unit, note: String? = null) {
    SettingsRow(
        title,
        Modifier.clickable { onChange(!on) },
        subtitle = note,
        trailing = { Switch(checked = on, onCheckedChange = null) },
    )
}
