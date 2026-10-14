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
import com.winlator.core.WineRegistryEditor
import java.io.File

/**
 * Choices the client normally asks for on first run (region, intro movie, photosensitivity and
 * screen narrator prompts). The client saves them to Config.wtf, and Wine saves the region to
 * user.reg, only on a clean exit, which never happens here, so they were asked again on every
 * launch. The launcher now stores them and writes them before each boot.
 */
object GameSettings {
    enum class Region(val label: String, val code: String?) {
        ASK("Ask in game", null),
        US("Americas", "US"),
        EU("Europe", "EU"),
        KR("Korea", "KR"),
        TW("Taiwan", "TW"),
    }

    /**
     * The game's render size. "Fit screen" options match the aspect ratio of the screen the game
     * is launched on (a foldable's inner or cover screen) at roughly the pixel count of the named
     * 16:9 size, so they cost about the same GPU time.
     */
    enum class Resolution(val label: String, val pixels: Int? = null, val fixed: String? = null) {
        AUTO("Default (720p on Snapdragon 8 Elite, else 1080p)"),
        FIT_720("Fit screen, 720p class", pixels = 1280 * 720),
        FIT_900("Fit screen, 900p class", pixels = 1600 * 900),
        FIT_1080("Fit screen, 1080p class", pixels = 1920 * 1080),
        P720("1280 x 720 (16:9)", fixed = "1280x720"),
        P900("1600 x 900 (16:9)", fixed = "1600x900"),
        P1080("1920 x 1080 (16:9)", fixed = "1920x1080"),
    }

    data class Settings(
        val region: Region = Region.ASK,
        val resolution: Resolution = Resolution.AUTO,
        val skipIntro: Boolean = true,
        val skipPhotosensitivity: Boolean = true,
        val skipNarrator: Boolean = true,
        val controllerLightsOff: Boolean = false,
        val forceGamepadUiOn: Boolean = true,
        val hideGamepadBars: Boolean = false,
    ) {
        /** InputDeviceInterfaceStyle (the in-game "Gamepad UI (Alpha)" option) to force, or null to leave it alone. */
        val gamepadUi: String? get() = if (forceGamepadUiOn) "1" else null
    }

    fun load(context: Context): Settings {
        val p = prefs(context)
        val d = Settings()
        return Settings(
            region = runCatching { Region.valueOf(p.getString(KEY_REGION, d.region.name)!!) }.getOrDefault(d.region),
            resolution = runCatching { Resolution.valueOf(p.getString(KEY_RESOLUTION, d.resolution.name)!!) }.getOrDefault(d.resolution),
            skipIntro = p.getBoolean(KEY_SKIP_INTRO, d.skipIntro),
            skipPhotosensitivity = p.getBoolean(KEY_SKIP_PHOTO, d.skipPhotosensitivity),
            skipNarrator = p.getBoolean(KEY_SKIP_NARRATOR, d.skipNarrator),
            controllerLightsOff = p.getBoolean(KEY_CONTROLLER_LIGHTS_OFF, d.controllerLightsOff),
            forceGamepadUiOn = p.getBoolean(KEY_FORCE_GAMEPAD_UI_ON, d.forceGamepadUiOn),
            hideGamepadBars = p.getBoolean(KEY_HIDE_GAMEPAD_BARS, d.hideGamepadBars),
        )
    }

    fun save(context: Context, s: Settings) {
        prefs(context).edit()
            .putString(KEY_REGION, s.region.name)
            .putString(KEY_RESOLUTION, s.resolution.name)
            .putBoolean(KEY_SKIP_INTRO, s.skipIntro)
            .putBoolean(KEY_SKIP_PHOTO, s.skipPhotosensitivity)
            .putBoolean(KEY_SKIP_NARRATOR, s.skipNarrator)
            .putBoolean(KEY_CONTROLLER_LIGHTS_OFF, s.controllerLightsOff)
            .putBoolean(KEY_FORCE_GAMEPAD_UI_ON, s.forceGamepadUiOn)
            .putBoolean(KEY_HIDE_GAMEPAD_BARS, s.hideGamepadBars)
            .apply()
    }

    /** Render size as "WxH" (landscape) for a display of [displayWidth] x [displayHeight]. */
    fun screenSize(r: Resolution, default: String, displayWidth: Int, displayHeight: Int): String {
        r.fixed?.let { return it }
        val pixels = r.pixels ?: return default
        val long = maxOf(displayWidth, displayHeight)
        val short = minOf(displayWidth, displayHeight)
        if (short <= 0) return default
        val aspect = long.toDouble() / short
        val height = kotlin.math.sqrt(pixels / aspect)
        fun even8(v: Double, cap: Int) = (Math.round(v / 8.0).toInt() * 8).coerceIn(8, maxOf(8, cap - cap % 8))
        return "${even8(height * aspect, long)}x${even8(height, short)}"
    }

    /** CVar -> value for Config.wtf; null removes the line. */
    fun configOverrides(s: Settings): Map<String, String?> = buildMap {
        // ponytail: "99" is above any expansion level, so the intro counts as seen
        put("playIntroMovie", if (s.skipIntro) "99" else null)
        put("showPhotosensitivityWarning", if (s.skipPhotosensitivity) "0" else "1")
        put("showScreenNarrationDialog", if (s.skipNarrator) "0" else "1")
        if (s.skipNarrator) put("accessibilityScreenNarrationEnabled", "0")
    }

    /** Drops every SET line for an overridden key, then appends the non-null overrides. */
    fun applyOverrides(lines: List<String>, overrides: Map<String, String?>): List<String> {
        fun keyOf(line: String) = line.takeIf { it.startsWith("SET ", ignoreCase = true) }
            ?.removeRange(0, 4)?.trim()?.substringBefore(' ')
        return lines.filterNot { line -> keyOf(line)?.let { k -> overrides.keys.any { it.equals(k, ignoreCase = true) } } == true } +
            overrides.mapNotNull { (k, v) -> v?.let { "SET $k \"$it\"" } }
    }

    fun applyRegion(userReg: File, s: Settings) {
        WineRegistryEditor(userReg).use { reg ->
            val code = s.region.code
            if (code == null) {
                reg.removeValue(LAUNCH_KEY, "REGION")
            } else {
                reg.setStringValue(LAUNCH_KEY, "REGION", code)
                reg.setStringValue(LAUNCH_KEY, "LOCALE", LOCALE)
                reg.setStringValue(LAUNCH_KEY, "LOCALE_AUDIO", LOCALE)
                reg.setStringValue("Software\\Blizzard Entertainment\\Launcher", "Locale", LOCALE)
            }
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val LAUNCH_KEY = "Software\\Blizzard Entertainment\\Battle.net\\Launch Options\\WoW"
    // ponytail: matches the textLocale default in Config.wtf; add a language picker if someone needs one
    private const val LOCALE = "enUS"
    private const val PREFS = "game_settings"
    private const val KEY_REGION = "region"
    private const val KEY_RESOLUTION = "resolution"
    private const val KEY_SKIP_INTRO = "skip_intro"
    private const val KEY_SKIP_PHOTO = "skip_photosensitivity"
    private const val KEY_SKIP_NARRATOR = "skip_narrator"
    private const val KEY_CONTROLLER_LIGHTS_OFF = "controller_lights_off"
    private const val KEY_FORCE_GAMEPAD_UI_ON = "force_gamepad_ui_on"
    private const val KEY_HIDE_GAMEPAD_BARS = "hide_gamepad_bars"
}

@Composable
fun GameSettingsDialog(
    initial: GameSettings.Settings,
    onDismiss: () -> Unit,
    onConfirm: (GameSettings.Settings) -> Unit,
) {
    var s by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Game settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Applied every time the game starts.")
                Spacer(Modifier.height(12.dp))
                Text("Region")
                GameSettings.Region.entries.forEach { r ->
                    Row(
                        Modifier.selectable(selected = s.region == r, onClick = { s = s.copy(region = r) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = s.region == r, onClick = null)
                        Text(r.label)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Resolution (fit screen uses the screen you launch on)")
                GameSettings.Resolution.entries.forEach { r ->
                    Row(
                        Modifier.selectable(selected = s.resolution == r, onClick = { s = s.copy(resolution = r) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = s.resolution == r, onClick = null)
                        Text(r.label)
                    }
                }
                Spacer(Modifier.height(12.dp))
                SettingCheck("Skip intro cinematic", s.skipIntro) { s = s.copy(skipIntro = it) }
                SettingCheck("Skip photosensitivity warning", s.skipPhotosensitivity) { s = s.copy(skipPhotosensitivity = it) }
                SettingCheck("Skip screen narrator prompt (narrator off)", s.skipNarrator) { s = s.copy(skipNarrator = it) }
                SettingCheck("Turn off controller lights (Android 12+)", s.controllerLightsOff) { s = s.copy(controllerLightsOff = it) }
                SettingCheck("Force Gamepad UI (Alpha) mode on at every start", s.forceGamepadUiOn) { s = s.copy(forceGamepadUiOn = it) }
                SettingCheck("Hide Blizzard's gamepad action bars (keeps gamepad mode)", s.hideGamepadBars) { s = s.copy(hideGamepadBars = it) }
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
