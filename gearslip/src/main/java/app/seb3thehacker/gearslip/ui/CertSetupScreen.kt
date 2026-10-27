package app.seb3thehacker.gearslip.ui

import androidx.compose.ui.res.stringResource
import app.seb3thehacker.gearslip.R
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.seb3thehacker.gearslip.AppSettings
import app.seb3thehacker.gearslip.CertProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shown when Gearslip has no certificate loaded. Download is the one clear path; importing your
 * own file is offered as a smaller, secondary option underneath it.
 *
 * The app ships no certificates. Skipping generates a self-signed one, which a real head unit
 * rejects.
 */
@Composable
fun CertSetupScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var downloadState by remember { mutableStateOf<DownloadState>(DownloadState.Idle) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var manualProgress by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingUri = uri
    }

    val installed = manualProgress || downloadState is DownloadState.Success

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(R.string.certsetup_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (installed) {
                    stringResource(R.string.certsetup_loaded)
                } else {
                    stringResource(R.string.certsetup_choose)
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))

            if (installed) {
                Text(
                    if (manualProgress) stringResource(R.string.cert_imported) else stringResource(R.string.certsetup_installed),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                // A downloaded certificate works in most cars, not all: say where to go when it doesn't.
                if (!manualProgress) {
                    Text(
                        stringResource(R.string.certsetup_installed_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    Text(stringResource(R.string.certsetup_continue))
                }
                Spacer(Modifier.height(40.dp))
                return@Column
            }

            when (downloadState) {
                is DownloadState.Idle -> {
                    Button(
                        onClick = {
                            downloadState = DownloadState.Loading
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { CertProvider.downloadAndImport(context) }
                                }
                                downloadState = result.fold(
                                    onSuccess = { DownloadState.Success(it.certificate.subjectX500Principal.name) },
                                    onFailure = { DownloadState.Error(it.message ?: "Unknown error") },
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Text(stringResource(R.string.cert_download))
                    }
                    Text(
                        stringResource(R.string.certsetup_aasdk_sub),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                is DownloadState.Loading -> {
                    Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                        DownloadingRow {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text(stringResource(R.string.cert_downloading), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }

                is DownloadState.Success -> Unit // handled above via `installed`

                is DownloadState.Error -> {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                stringResource(R.string.certsetup_failed),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                (downloadState as DownloadState.Error).message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Button(onClick = { downloadState = DownloadState.Idle }, modifier = Modifier.fillMaxWidth()) {
                                Text(stringResource(R.string.certsetup_retry))
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.cert_import)) }
            Text(
                stringResource(R.string.certsetup_import_sub),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    AppSettings.setSkippedCertSetup(context)
                    onDone()
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(stringResource(R.string.certsetup_skip))
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    pendingUri?.let { uri ->
        PasswordDialog(
            onDismiss = { pendingUri = null },
            onConfirm = { password, reportError ->
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        runCatching { CertProvider.importFrom(context, uri, password) }
                    }
                    result.onSuccess {
                        pendingUri = null
                        manualProgress = true
                    }.onFailure {
                        reportError(context.getString(R.string.cert_import_failed))
                    }
                }
            },
        )
    }
}

/** A tight horizontal row for the inline stringResource(R.string.cert_downloading) spinner state. */
@Composable
private fun DownloadingRow(content: @Composable RowScope.() -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

private sealed class DownloadState {
    data object Idle : DownloadState()
    data object Loading : DownloadState()
    data class Success(val subjectName: String) : DownloadState()
    data class Error(val message: String) : DownloadState()
}

@Composable
private fun PasswordDialog(onDismiss: () -> Unit, onConfirm: (String, (String) -> Unit) -> Unit) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cert_password_title)) },
        text = {
            OutlinedTextField(
                value = password,
                onValueChange = { password = it; error = null },
                singleLine = true,
                label = { Text(stringResource(R.string.cert_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = error != null,
                supportingText = { Text(error ?: stringResource(R.string.cert_password_hint)) },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(password) { error = it } }) { Text(stringResource(R.string.cert_import_button)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
