package app.gamenative.ui.screen.wow

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@Composable
fun AddonsDialog(gameRoot: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var installed by remember { mutableStateOf<List<Addons.Installed>>(emptyList()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmRemove by remember { mutableStateOf<Addons.Installed?>(null) }

    suspend fun refresh() {
        installed = withContext(Dispatchers.IO) { Addons.list(gameRoot) }
    }

    fun run(label: String, action: suspend () -> String) {
        if (busy != null) return
        busy = label
        message = null
        scope.launch {
            message = try {
                action()
            } catch (e: Exception) {
                Timber.w(e, "Addon action failed: %s", label)
                "Failed: ${e.message ?: e.javaClass.simpleName}"
            }
            refresh()
            busy = null
        }
    }

    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        run("Installing addon...") {
            val addon = withContext(Dispatchers.IO) {
                val input = context.contentResolver.openInputStream(uri) ?: error("Couldn't open the file")
                input.use { Addons.installZip(gameRoot, it, context.cacheDir) }
            }
            "Installed ${addon.title}."
        }
    }

    LaunchedEffect(gameRoot) { refresh() }

    AlertDialog(
        onDismissRequest = { if (busy == null) onDismiss() },
        title = { Text("Addons") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Installed in ${WowClientDownloader.FLAVOR_DIR}/Interface/AddOns. Changes apply the next time the game starts.")
                Spacer(Modifier.height(12.dp))
                if (installed.isEmpty()) {
                    Text("No addons installed.")
                }
                installed.forEach { addon ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(addon.title)
                            addon.version?.let { Text("Version $it") }
                        }
                        TextButton(onClick = { confirmRemove = addon }, enabled = busy == null) { Text("Remove") }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                val hasConsolePort = installed.any { it.name == Addons.CONSOLEPORT }
                TextButton(
                    enabled = busy == null,
                    onClick = {
                        run("Downloading ConsolePort...") {
                            val addon = Addons.installConsolePort(gameRoot, context.cacheDir) { p ->
                                busy = "Downloading ConsolePort... ${(p * 100).toInt()}%"
                            }
                            "Installed ${addon.title} ${addon.version.orEmpty()}."
                        }
                    },
                ) { Text(if (hasConsolePort) "Update ConsolePort (controller UI)" else "Add ConsolePort (controller UI)") }
                TextButton(
                    enabled = busy == null,
                    onClick = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                ) { Text("Add from .zip file") }
                busy?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it)
                }
                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = busy == null) { Text("Done") } },
    )

    confirmRemove?.let { addon ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Remove ${addon.title}?") },
            text = { Text("Deletes ${addon.folders.joinToString()} from the AddOns folder.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = null
                    run("Removing ${addon.title}...") {
                        withContext(Dispatchers.IO) { Addons.remove(gameRoot, addon) }
                        "Removed ${addon.title}."
                    }
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
}
