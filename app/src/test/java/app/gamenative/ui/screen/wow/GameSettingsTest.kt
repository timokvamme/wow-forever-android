package app.gamenative.ui.screen.wow

import org.junit.Assert.assertEquals
import org.junit.Test

class GameSettingsTest {
    @Test
    fun overridesReplaceRemoveAndAreStable() {
        val lines = listOf("SET gxApi \"D3D11\"", "SET playintromovie \"3\"", "SET showPhotosensitivityWarning \"1\"")
        val skipAll = GameSettings.configOverrides(GameSettings.Settings())
        val once = GameSettings.applyOverrides(lines, skipAll)
        assertEquals(
            listOf(
                "SET gxApi \"D3D11\"",
                "SET playIntroMovie \"99\"",
                "SET showPhotosensitivityWarning \"0\"",
                "SET showScreenNarrationDialog \"0\"",
                "SET accessibilityScreenNarrationEnabled \"0\"",
                "SET InputDeviceInterfaceStyle \"1\"",
            ),
            once,
        )
        assertEquals(once, GameSettings.applyOverrides(once, skipAll)) // no rewrite on the next boot

        val keepIntro = GameSettings.configOverrides(GameSettings.Settings(skipIntro = false, skipNarrator = false))
        val off = GameSettings.applyOverrides(once, keepIntro)
        assertEquals(false, off.any { it.contains("playIntroMovie") })
        assertEquals(true, off.contains("SET showScreenNarrationDialog \"1\""))

        val noGamepadUi = GameSettings.applyOverrides(once, GameSettings.configOverrides(GameSettings.Settings(gamepadUi = false)))
        assertEquals(true, noGamepadUi.contains("SET InputDeviceInterfaceStyle \"0\""))
    }
}
