package app.seb3thehacker.gearslip.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.seb3thehacker.gearslip.R
import app.seb3thehacker.gearslip.car.SavedPlace
import app.seb3thehacker.gearslip.car.SavedPlaces
import kotlinx.coroutines.launch

/** Home, Work and three named places, each saved the moment it's typed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedPlacesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val places = remember { SavedPlaces.all(context).toMutableList() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.places_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(R.string.places_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            for (i in 0 until SavedPlaces.COUNT) {
                PlaceCard(i, places[i]) { place ->
                    places[i] = place
                    SavedPlaces.set(context, i, place)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PlaceCard(index: Int, initial: SavedPlace, onChange: (SavedPlace) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var label by remember { mutableStateOf(initial.label) }
    var address by remember { mutableStateOf(initial.address) }
    var finding by remember { mutableStateOf(false) }
    var notFound by remember { mutableStateOf(false) }
    val custom = index > SavedPlaces.WORK

    fun save() = onChange(SavedPlace(if (custom) label.trim() else "", address.trim()))
    fun findHere() {
        finding = true
        notFound = false
        scope.launch {
            val here = SavedPlaces.here(context)
            finding = false
            if (here == null) {
                notFound = true
            } else {
                address = here
                save()
            }
        }
    }
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (SavedPlaces.hasLocationPermission(context)) findHere() else notFound = true
    }

    SettingsCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    when (index) {
                        SavedPlaces.HOME -> Icons.Filled.Home
                        SavedPlaces.WORK -> Briefcase
                        else -> Icons.Filled.Place
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    when (index) {
                        SavedPlaces.HOME -> stringResource(R.string.places_home)
                        SavedPlaces.WORK -> stringResource(R.string.places_work)
                        else -> label.ifBlank { stringResource(R.string.places_other, index - SavedPlaces.WORK) }
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            if (custom) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it; save() },
                    label = { Text(stringResource(R.string.places_name)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            OutlinedTextField(
                value = address,
                onValueChange = { address = it; notFound = false; save() },
                label = { Text(stringResource(R.string.places_address)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
            if (notFound) {
                Text(
                    stringResource(R.string.places_not_found),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        if (SavedPlaces.hasLocationPermission(context)) {
                            findHere()
                        } else {
                            askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    },
                    enabled = !finding,
                ) {
                    if (finding) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(if (finding) R.string.places_finding else R.string.places_here))
                }
                if (address.isNotBlank() || label.isNotBlank()) {
                    TextButton(onClick = { label = ""; address = ""; notFound = false; save() }) {
                        Text(stringResource(R.string.places_clear))
                    }
                }
            }
        }
    }
}

/** Material's "work" briefcase, which only ships in the extended icon set. */
private val Briefcase: ImageVector by lazy {
    materialIcon("Filled.Work") {
        materialPath {
            moveTo(20f, 6f); horizontalLineToRelative(-4f); verticalLineTo(4f)
            curveToRelative(0f, -1.11f, -0.89f, -2f, -2f, -2f); horizontalLineToRelative(-4f)
            curveTo(8.89f, 2f, 8f, 2.89f, 8f, 4f); verticalLineToRelative(2f); horizontalLineTo(4f)
            curveToRelative(-1.11f, 0f, -1.99f, 0.89f, -1.99f, 2f); lineTo(2f, 19f)
            curveToRelative(0f, 1.11f, 0.89f, 2f, 2f, 2f); horizontalLineToRelative(16f)
            curveToRelative(1.11f, 0f, 2f, -0.89f, 2f, -2f); verticalLineTo(8f)
            curveToRelative(0f, -1.11f, -0.89f, -2f, -2f, -2f); close()
            moveTo(14f, 6f); horizontalLineToRelative(-4f); verticalLineTo(4f); horizontalLineToRelative(4f)
            verticalLineToRelative(2f); close()
        }
    }
}
