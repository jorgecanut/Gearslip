package app.seb3thehacker.gearslip.ui

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import android.text.format.Formatter
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.seb3thehacker.gearslip.media.Lyrics
import app.seb3thehacker.gearslip.speech.SpeechModels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The screen behind "Manage storage" on Gearslip's page in the phone's App info › Storage.
 * Clears each kind of stored data on its own: the lyrics cache, the voice models, old logs and
 * other temporary files. Some Android versions drop their own "Clear storage" button once an app
 * has this screen, so deleting everything is offered here too.
 *
 * The manifest names it in full: a relative name would be read against the installed package,
 * and the debug build's ".dev" package has no such class.
 */
class ManageSpaceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { GearslipTheme { StorageScreen(onBack = ::finish) } }
    }
}

/** What each kind of stored data takes up, measured on a background thread. */
private class Usage(val lyricsSongs: Int, val lyrics: Long, val voice: Long, val logs: Long, val other: Long) {
    val total get() = lyrics + voice + logs + other
}

private fun File.size(): Long = walkBottomUp().filter { it.isFile }.sumOf { it.length() }

private fun lyricsDir(context: Context) = File(context.cacheDir, Lyrics.LYRICS_DIR)
private fun voiceDir(context: Context) = File(context.filesDir, "speech")

/** Every launch's log but the newest, which is the one this launch is still writing. */
private fun oldLogs(context: Context): List<File> {
    val dir = context.getExternalFilesDir(null) ?: return emptyList()
    val logs = dir.listFiles { f -> f.isFile && f.name.startsWith("gearslip-") && f.name.endsWith(".log") }
        ?.sortedByDescending { it.name }.orEmpty()
    return logs.drop(1) + listOfNotNull(File(context.cacheDir, "shared").takeIf { it.exists() })
}

/** The cache directory without the lyrics: downloaded updates, shared logs, spoken directions. */
private fun otherCache(context: Context): List<File> =
    context.cacheDir.listFiles()?.filter { it.name != Lyrics.LYRICS_DIR && it.name != "shared" }.orEmpty()

private fun measure(context: Context) = Usage(
    lyricsSongs = lyricsDir(context).listFiles()?.size ?: 0,
    lyrics = lyricsDir(context).size(),
    voice = voiceDir(context).size(),
    logs = oldLogs(context).sumOf { it.size() },
    other = otherCache(context).sumOf { it.size() },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StorageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var usage by remember { mutableStateOf<Usage?>(null) }
    LaunchedEffect(refresh) { usage = withContext(Dispatchers.IO) { measure(context) } }
    fun clear(work: () -> Unit) {
        scope.launch {
            withContext(Dispatchers.IO) { work() }
            refresh++
        }
    }
    fun size(bytes: Long) = Formatter.formatShortFileSize(context, bytes)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Gearslip storage") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            val u = usage
            Text(
                if (u == null) "Measuring…" else "Gearslip's data takes ${size(u.total)} of the phone's storage.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsCard {
                StorageRow(
                    "Lyrics",
                    u?.let { "${it.lyricsSongs} songs · ${size(it.lyrics)}. Cleared lyrics download again when you open them." },
                    enabled = (u?.lyrics ?: 0) > 0,
                    action = "Clear",
                ) { clear { Lyrics.clearCache(context) } }
                SettingsDivider()
                StorageRow(
                    "Voice models",
                    u?.let { "${size(it.voice)}. Voice commands need them; download them again in Settings." },
                    enabled = (u?.voice ?: 0) > 0,
                    action = "Remove",
                ) {
                    clear {
                        voiceDir(context).deleteRecursively()
                        SpeechModels.init(context)
                    }
                }
                SettingsDivider()
                StorageRow(
                    "Old logs",
                    u?.let { "${size(it.logs)}. Logs from earlier launches; this launch's log stays." },
                    enabled = (u?.logs ?: 0) > 0,
                    action = "Clear",
                ) { clear { oldLogs(context).forEach { it.deleteRecursively() } } }
                SettingsDivider()
                StorageRow(
                    "Other temporary files",
                    u?.let { "${size(it.other)}. Downloaded updates and other files Gearslip can make again." },
                    enabled = (u?.other ?: 0) > 0,
                    action = "Clear",
                ) { clear { otherCache(context).forEach { it.deleteRecursively() } } }
            }
            DeleteEverything()
        }
    }
}

@Composable
private fun StorageRow(title: String, detail: String?, enabled: Boolean, action: String, onClick: () -> Unit) {
    SettingsRow(
        title,
        subtitle = detail ?: "Measuring…",
        trailing = { TextButton(onClick = onClick, enabled = enabled) { Text(action) } },
    )
}

/** Android's "Clear storage", which this screen replaces: two taps, so it can't happen by accident. */
@Composable
private fun DeleteEverything() {
    val context = LocalContext.current
    var armed by remember { mutableStateOf(false) }
    SettingsCard {
        SettingsRow(
            "Delete all Gearslip data",
            subtitle = if (armed) {
                "Tap Delete again to erase settings, certificates, voice models and logs. Gearslip closes, and starts from setup next time."
            } else {
                "Settings, certificates, voice models, lyrics and logs."
            },
            titleColor = MaterialTheme.colorScheme.error,
            trailing = {
                TextButton(onClick = {
                    if (armed) context.getSystemService(ActivityManager::class.java)?.clearApplicationUserData()
                    else armed = true
                }) { Text(if (armed) "Delete" else "Delete…", color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}
