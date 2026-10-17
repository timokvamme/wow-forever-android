package app.gamenative.ui.screen.wow

import android.os.Environment
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
    var confirmReset by remember { mutableStateOf<Addons.Installed?>(null) }
    var webPageFor by remember { mutableStateOf<Addons.CatalogAddon?>(null) }

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

    fun install(cf: Addons.CatalogAddon) {
        run("Getting ${cf.title}...") {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val addon = Addons.installCatalog(cf, gameRoot, context.cacheDir, downloads) { p ->
                busy = "Downloading ${cf.title}... ${(p * 100).toInt()}%"
            }
            if (addon != null) {
                "Installed ${cf.title} ${addon.version.orEmpty()}."
            } else {
                webPageFor = cf
                "Opening CurseForge..."
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (busy == null) onDismiss() },
        title = { Text("Addons") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Installed in ${WowClientDownloader.FLAVOR_DIR}/Interface/AddOns. Changes apply the next time the game starts.")
                Spacer(Modifier.height(12.dp))
                val byCatalog = installed.groupBy { Addons.catalogIdOf(it) }
                Addons.CATALOG.forEach { cf ->
                    val have = byCatalog[cf.id].orEmpty()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(cf.title)
                            Text(
                                cf.description + when {
                                    have.isEmpty() -> ""
                                    else -> " · installed" + (have.first().version?.let { " $it" } ?: "")
                                },
                            )
                        }
                        TextButton(onClick = { install(cf) }, enabled = busy == null) { Text(if (have.isEmpty()) "Add" else "Update") }
                        if (have.isNotEmpty()) {
                            TextButton(onClick = { confirmReset = have.first() }, enabled = busy == null) { Text("Reset") }
                            TextButton(onClick = { confirmRemove = have.first() }, enabled = busy == null) { Text("Remove") }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    enabled = busy == null && Addons.CATALOG.any { it.installByDefault && byCatalog[it.id] == null },
                    onClick = {
                        run("Installing addons...") {
                            val missing = Addons.CATALOG.filter { it.installByDefault && byCatalog[it.id] == null }
                            val failed = missing.filter { cf ->
                                busy = "Installing ${cf.title}..."
                                runCatching { Addons.installCatalog(cf, gameRoot, context.cacheDir, null) {} }.getOrNull() == null
                            }
                            if (failed.isEmpty()) "Installed ${missing.size} addons." else "Couldn't download ${failed.joinToString { it.title }}. Tap Add on each to use the browser."
                        }
                    },
                ) { Text("Add all recommended addons") }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                val others = byCatalog[null].orEmpty()
                Text(if (others.isEmpty()) "No other addons installed." else "Other installed addons")
                others.forEach { addon ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(addon.title)
                            addon.version?.let { Text("Version $it") }
                        }
                        TextButton(onClick = { confirmReset = addon }, enabled = busy == null) { Text("Reset") }
                        TextButton(onClick = { confirmRemove = addon }, enabled = busy == null) { Text("Remove") }
                    }
                }
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

    webPageFor?.let { cf ->
        AddonWebDownloadDialog(
            title = cf.title,
            pageUrl = cf.downloadPage,
            onDownload = { download ->
                webPageFor = null
                run("Downloading ${cf.title}...") {
                    val addon = Addons.installFromBrowserDownload(
                        gameRoot, context.cacheDir, download.url, download.userAgent, download.cookies, download.fileName, cf.id,
                    ) { p -> busy = "Downloading ${cf.title}... ${(p * 100).toInt()}%" }
                    "Installed ${cf.title} ${addon.version.orEmpty()}."
                }
            },
            onDismiss = {
                webPageFor = null
                message = null
            },
        )
    }

    confirmReset?.let { addon ->
        AlertDialog(
            onDismissRequest = { confirmReset = null },
            title = { Text("Reset ${addon.title}?") },
            text = { Text("Deletes its saved settings for every character, so it starts fresh the next time the game starts.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = null
                    run("Resetting ${addon.title}...") {
                        val count = withContext(Dispatchers.IO) { Addons.resetSettings(gameRoot, addon) }
                        if (count == 0) "${addon.title} had no saved settings." else "Reset ${addon.title}."
                    }
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = null }) { Text("Cancel") } },
        )
    }

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
