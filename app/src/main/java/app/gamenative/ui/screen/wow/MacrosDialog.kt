package app.gamenative.ui.screen.wow

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File
import timber.log.Timber

/** Edits WoW macros with the phone's keyboard, for setups where typing in the game doesn't work. */
@Composable
fun MacrosDialog(gameRoot: File, onDismiss: () -> Unit) {
    val owners = remember(gameRoot) { runCatching { Macros.owners(gameRoot) }.getOrDefault(emptyList()) }
    var owner by remember { mutableStateOf(owners.firstOrNull()) }
    var macros by remember { mutableStateOf<List<Macros.Macro>>(emptyList()) }
    var editing by remember { mutableStateOf<Macros.Macro?>(null) }
    var isNew by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pickOwner by remember { mutableStateOf(false) }

    LaunchedEffect(owner) {
        macros = owner?.let { runCatching { Macros.load(it) }.getOrDefault(emptyList()) }.orEmpty()
        message = null
    }

    fun store(updated: List<Macros.Macro>, done: String) {
        val o = owner ?: return
        message = try {
            Macros.save(o, updated)
            Macros.keepLocalMacros(gameRoot)
            macros = updated
            done
        } catch (e: Exception) {
            Timber.w(e, "Saving macros failed")
            "Couldn't save: ${e.message}"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Macros") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (owners.isEmpty()) {
                    Text("No characters found yet. Log in to a character once, exit the game, and come back.")
                    return@Column
                }
                Text("Edit with the game closed. Changes show up the next time the game starts.")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { pickOwner = true }) { Text(owner?.label ?: "Pick a character") }
                    DropdownMenu(expanded = pickOwner, onDismissRequest = { pickOwner = false }) {
                        owners.forEach { o ->
                            DropdownMenuItem(text = { Text(o.label) }, onClick = {
                                owner = o
                                pickOwner = false
                                editing = null
                            })
                        }
                    }
                }
                HorizontalDivider()
                val current = editing
                if (current != null) {
                    var name by remember(current) { mutableStateOf(current.name) }
                    var body by remember(current) { mutableStateOf(current.body) }
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(Macros.MAX_NAME) },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it.take(Macros.MAX_BODY) },
                        label = { Text("Macro (${body.length}/${Macros.MAX_BODY})") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row {
                        TextButton(enabled = name.isNotBlank(), onClick = {
                            val saved = current.copy(name = name.trim(), body = body.trimEnd())
                            val updated = if (isNew) macros + saved else macros.map { if (it.id == current.id) saved else it }
                            store(updated, "Saved ${saved.name}.")
                            editing = null
                        }) { Text("Save") }
                        if (!isNew) {
                            TextButton(onClick = {
                                store(macros.filterNot { it.id == current.id }, "Deleted ${current.name}.")
                                editing = null
                            }) { Text("Delete") }
                        }
                        TextButton(onClick = { editing = null }) { Text("Cancel") }
                    }
                } else {
                    if (macros.isEmpty()) Text("No macros here yet.")
                    macros.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name)
                                Text(m.body.lines().firstOrNull().orEmpty())
                            }
                            TextButton(onClick = {
                                isNew = false
                                editing = m
                            }) { Text("Edit") }
                        }
                    }
                    TextButton(onClick = {
                        isNew = true
                        editing = Macros.newMacro(macros, "", "")
                    }) { Text("New macro") }
                }
                message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
