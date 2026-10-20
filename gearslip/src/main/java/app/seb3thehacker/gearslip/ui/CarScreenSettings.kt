package app.seb3thehacker.gearslip.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.seb3thehacker.gearslip.car.CarSettings
import app.seb3thehacker.gearslip.car.PreviewSize
import app.seb3thehacker.gearslip.car.ScreenFit
import app.seb3thehacker.gearslip.car.ScreenSize

/**
 * The car screen's real size, for a head unit that reports the wrong one and stretches the
 * picture, and the size the phone's car preview draws at. The same screen size and fit are in
 * the car's own Settings; typing in any size is only here.
 */
@Composable
internal fun CarScreenSection() {
    val size by CarSettings.screenSize.collectAsState()
    val fit by CarSettings.screenFit.collectAsState()
    val preview by CarSettings.previewSize.collectAsState()
    val previewCustom by CarSettings.previewCustom.collectAsState()
    var editingCar by remember { mutableStateOf(false) }
    var editingPreview by remember { mutableStateOf(false) }

    if (editingCar) {
        SizeDialog(
            title = "Car screen size",
            note = "Use this if the car's picture looks stretched. Enter the screen's real size, " +
                "such as 1280x480. It takes effect the next time you connect.",
            initial = size,
            clearLabel = "Use the car's",
            onDismiss = { editingCar = false },
            onSave = { CarSettings.setScreenSize(it); editingCar = false },
        )
    }
    if (editingPreview) {
        SizeDialog(
            title = "Preview size",
            note = "The size the car preview draws at, such as 1920x720.",
            initial = previewCustom,
            clearLabel = null,
            onDismiss = { editingPreview = false },
            onSave = { custom ->
                CarSettings.setPreviewSize(if (custom == null) PreviewSize.LAST_CAR else PreviewSize.CUSTOM, custom)
                editingPreview = false
            },
        )
    }

    SettingsSection(
        "Car screen",
        footer = "Gearslip uses the size the car reports unless you set one.",
    ) {
        SettingsRow(
            "Screen size",
            Modifier.clickable { editingCar = true },
            subtitle = size?.let { "$it, set by you" } ?: "From the car",
        )
        if (size != null) {
            SettingsDivider()
            SettingsRow(
                "Crop the edges",
                Modifier.selectable(fit == ScreenFit.CROP, role = Role.RadioButton) { CarSettings.setScreenFit(ScreenFit.CROP) },
                subtitle = "For a car that cuts off the top and bottom, or the sides",
                trailing = { RadioButton(selected = fit == ScreenFit.CROP, onClick = null) },
            )
            SettingsDivider()
            SettingsRow(
                "Squash to fit",
                Modifier.selectable(fit == ScreenFit.SQUASH, role = Role.RadioButton) { CarSettings.setScreenFit(ScreenFit.SQUASH) },
                subtitle = "For a car that squeezes the whole picture, so circles look like ovals",
                trailing = { RadioButton(selected = fit == ScreenFit.SQUASH, onClick = null) },
            )
        }
    }

    SettingsSection("Car preview size") {
        SettingsRow(
            "Last car",
            Modifier.selectable(preview == PreviewSize.LAST_CAR, role = Role.RadioButton) {
                CarSettings.setPreviewSize(PreviewSize.LAST_CAR)
            },
            subtitle = "The size of the last car you connected to",
            trailing = { RadioButton(selected = preview == PreviewSize.LAST_CAR, onClick = null) },
        )
        SettingsDivider()
        SettingsRow(
            "This screen",
            Modifier.selectable(preview == PreviewSize.FILL, role = Role.RadioButton) {
                CarSettings.setPreviewSize(PreviewSize.FILL)
            },
            subtitle = "Fills this device's screen, for a head unit running Gearslip itself",
            trailing = { RadioButton(selected = preview == PreviewSize.FILL, onClick = null) },
        )
        SettingsDivider()
        SettingsRow(
            "Custom",
            Modifier.selectable(preview == PreviewSize.CUSTOM, role = Role.RadioButton) {
                if (previewCustom == null) editingPreview = true else CarSettings.setPreviewSize(PreviewSize.CUSTOM)
            },
            subtitle = previewCustom?.toString() ?: "Enter a size",
            trailing = { RadioButton(selected = preview == PreviewSize.CUSTOM, onClick = null) },
        )
        if (preview == PreviewSize.CUSTOM) {
            SettingsDivider()
            SettingsRow("Change the custom size", Modifier.clickable { editingPreview = true })
        }
    }
}

/** Asks for a width and height; [clearLabel], when given, offers going back to no size at all. */
@Composable
private fun SizeDialog(
    title: String,
    note: String,
    initial: ScreenSize?,
    clearLabel: String?,
    onDismiss: () -> Unit,
    onSave: (ScreenSize?) -> Unit,
) {
    var width by remember { mutableStateOf(initial?.width?.toString().orEmpty()) }
    var height by remember { mutableStateOf(initial?.height?.toString().orEmpty()) }
    val parsed = ScreenSize.parse("${width}x$height")
    val number = KeyboardOptions(keyboardType = KeyboardType.Number)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(note)
                OutlinedTextField(width, { width = it.filter(Char::isDigit) }, label = { Text("Width") }, singleLine = true, keyboardOptions = number)
                OutlinedTextField(height, { height = it.filter(Char::isDigit) }, label = { Text("Height") }, singleLine = true, keyboardOptions = number)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(parsed) }, enabled = parsed != null) { Text("Save") } },
        dismissButton = {
            if (clearLabel != null) TextButton(onClick = { onSave(null) }) { Text(clearLabel) }
            else TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
