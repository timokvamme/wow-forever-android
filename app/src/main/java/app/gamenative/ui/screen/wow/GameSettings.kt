package app.gamenative.ui.screen.wow

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Choices the client normally asks for on first run (intro movie, photosensitivity and screen
 * narrator prompts) plus the Gamepad UI (Alpha) option. The client saves them to Config.wtf only
 * on a clean exit, which rarely happens here, so the launcher stores them and writes them before
 * each boot. Region and resolution are picked on the launcher's main screen.
 */
object GameSettings {
    data class Settings(
        val skipIntro: Boolean = true,
        val skipPhotosensitivity: Boolean = true,
        val skipNarrator: Boolean = true,
        val controllerLightsOff: Boolean = false,
        val forceGamepadUiOn: Boolean = true,
        /** Game account (as WoW lists it, e.g. "SOULPOWER") preselected at login; blank leaves WoW's choice. */
        val gameAccount: String = "",
    ) {
        /** InputDeviceInterfaceStyle, the in-game "Gamepad UI (Alpha)" option, written on every start. */
        val gamepadUi: String get() = if (forceGamepadUiOn) "1" else "0"
    }

    fun load(context: Context): Settings {
        val p = prefs(context)
        val d = Settings()
        return Settings(
            skipIntro = p.getBoolean(KEY_SKIP_INTRO, d.skipIntro),
            skipPhotosensitivity = p.getBoolean(KEY_SKIP_PHOTO, d.skipPhotosensitivity),
            skipNarrator = p.getBoolean(KEY_SKIP_NARRATOR, d.skipNarrator),
            controllerLightsOff = p.getBoolean(KEY_CONTROLLER_LIGHTS_OFF, d.controllerLightsOff),
            forceGamepadUiOn = p.getBoolean(KEY_FORCE_GAMEPAD_UI_ON, d.forceGamepadUiOn),
            gameAccount = p.getString(KEY_GAME_ACCOUNT, d.gameAccount).orEmpty(),
        )
    }

    fun save(context: Context, s: Settings) {
        prefs(context).edit()
            .putBoolean(KEY_SKIP_INTRO, s.skipIntro)
            .putBoolean(KEY_SKIP_PHOTO, s.skipPhotosensitivity)
            .putBoolean(KEY_SKIP_NARRATOR, s.skipNarrator)
            .putBoolean(KEY_CONTROLLER_LIGHTS_OFF, s.controllerLightsOff)
            .putBoolean(KEY_FORCE_GAMEPAD_UI_ON, s.forceGamepadUiOn)
            .putString(KEY_GAME_ACCOUNT, s.gameAccount.trim())
            .apply()
    }

    /** CVar -> value for Config.wtf; null removes the line. */
    fun configOverrides(s: Settings): Map<String, String?> = buildMap {
        // ponytail: "99" is above any expansion level, so the intro counts as seen
        put("playIntroMovie", if (s.skipIntro) "99" else null)
        put("showPhotosensitivityWarning", if (s.skipPhotosensitivity) "0" else "1")
        put("showScreenNarrationDialog", if (s.skipNarrator) "0" else "1")
        if (s.skipNarrator) put("accessibilityScreenNarrationEnabled", "0")
    }

    /** Game account names in Config.wtf's accountList ("!SOULPOWER|TIMOANDREAS|"), without the "!" mark. */
    fun accountsIn(lines: List<String>): List<String> =
        accountListValue(lines)?.split('|')?.map { it.trim().removePrefix("!") }?.filter { it.isNotEmpty() }.orEmpty()

    /**
     * Marks [account] with "!" in accountList, which is how WoW remembers the game account to preselect
     * at login. The account is added to the list if WoW hasn't listed it yet. A blank name changes nothing.
     */
    fun selectAccount(lines: List<String>, account: String): List<String> {
        val wanted = account.trim()
        if (wanted.isEmpty()) return lines
        val names = accountsIn(lines).let { known -> if (known.any { it.equals(wanted, ignoreCase = true) }) known else known + wanted }
        val value = names.joinToString("") { (if (it.equals(wanted, ignoreCase = true)) "!$it" else it) + "|" }
        return applyOverrides(lines, mapOf(ACCOUNT_LIST to value))
    }

    private fun accountListValue(lines: List<String>): String? =
        lines.lastOrNull { it.trim().startsWith("SET $ACCOUNT_LIST ", ignoreCase = true) }
            ?.trim()?.removeRange(0, 4 + ACCOUNT_LIST.length)?.trim()?.removeSurrounding("\"")

    private const val ACCOUNT_LIST = "accountList"

    /** Drops every SET line for an overridden key, then appends the non-null overrides. */
    fun applyOverrides(lines: List<String>, overrides: Map<String, String?>): List<String> {
        fun keyOf(line: String) = line.takeIf { it.startsWith("SET ", ignoreCase = true) }
            ?.removeRange(0, 4)?.trim()?.substringBefore(' ')
        return lines.filterNot { line -> keyOf(line)?.let { k -> overrides.keys.any { it.equals(k, ignoreCase = true) } } == true } +
            overrides.mapNotNull { (k, v) -> v?.let { "SET $k \"$it\"" } }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "game_settings"
    private const val KEY_SKIP_INTRO = "skip_intro"
    private const val KEY_SKIP_PHOTO = "skip_photosensitivity"
    private const val KEY_SKIP_NARRATOR = "skip_narrator"
    private const val KEY_CONTROLLER_LIGHTS_OFF = "controller_lights_off"
    private const val KEY_FORCE_GAMEPAD_UI_ON = "force_gamepad_ui_on"
    private const val KEY_GAME_ACCOUNT = "game_account"
}

@Composable
fun GameSettingsDialog(
    initial: GameSettings.Settings,
    knownAccounts: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (GameSettings.Settings) -> Unit,
) {
    var s by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Game settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Applied every time the game starts. Region and resolution are on the main screen.")
                Spacer(Modifier.height(12.dp))
                SettingCheck("Skip intro cinematic", s.skipIntro) { s = s.copy(skipIntro = it) }
                SettingCheck("Skip photosensitivity warning", s.skipPhotosensitivity) { s = s.copy(skipPhotosensitivity = it) }
                SettingCheck("Skip screen narrator prompt (narrator off)", s.skipNarrator) { s = s.copy(skipNarrator = it) }
                SettingCheck("Turn off controller lights (Android 12+)", s.controllerLightsOff) { s = s.copy(controllerLightsOff = it) }
                SettingCheck("Gamepad UI (Alpha)", s.forceGamepadUiOn) { s = s.copy(forceGamepadUiOn = it) }
                Spacer(Modifier.height(12.dp))
                Text("Game account selected at login")
                val choices = listOf("") + (knownAccounts + listOfNotNull(s.gameAccount.ifBlank { null }))
                    .distinctBy { it.uppercase() }
                choices.forEach { name ->
                    Row(
                        Modifier.selectable(selected = s.gameAccount.equals(name, ignoreCase = true), onClick = { s = s.copy(gameAccount = name) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = s.gameAccount.equals(name, ignoreCase = true), onClick = null)
                        Text(name.ifEmpty { "Game's choice" })
                    }
                }
                OutlinedTextField(
                    value = s.gameAccount,
                    onValueChange = { s = s.copy(gameAccount = it.uppercase()) },
                    label = { Text("Account name") },
                    singleLine = true,
                )
                if (knownAccounts.isEmpty()) {
                    Text("Log in once so the game lists your accounts, or type the name as the game shows it.")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(s) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SettingCheck(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.toggleable(value = checked, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label)
    }
}
