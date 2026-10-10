package app.gamenative.ui.screen.wow

import app.gamenative.ui.screen.wow.GameSettings.Resolution
import org.junit.Assert.assertEquals
import org.junit.Test

class GameSettingsResolutionTest {
    @Test
    fun `fit options match the aspect of a foldable's inner and cover screens`() {
        assertEquals("1000x920", GameSettings.screenSize(Resolution.FIT_720, "1280x720", 2352, 2172))
        assertEquals("1440x640", GameSettings.screenSize(Resolution.FIT_720, "1280x720", 2376, 1060))
        assertEquals("1496x1384", GameSettings.screenSize(Resolution.FIT_1080, "1280x720", 2172, 2352))
    }

    @Test
    fun `fixed and default options ignore the display`() {
        assertEquals("1600x900", GameSettings.screenSize(Resolution.P900, "1280x720", 2352, 2172))
        assertEquals("1280x720", GameSettings.screenSize(Resolution.AUTO, "1280x720", 2352, 2172))
        assertEquals("1280x720", GameSettings.screenSize(Resolution.FIT_900, "1280x720", 0, 0))
    }

    @Test
    fun `fit options never exceed the native screen size`() {
        assertEquals("1280x720", GameSettings.screenSize(Resolution.FIT_1080, "1920x1080", 1280, 720))
    }
}
