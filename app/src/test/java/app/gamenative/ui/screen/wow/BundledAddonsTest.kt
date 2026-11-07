package app.gamenative.ui.screen.wow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledAddonsTest {
    @Test
    fun `interface number comes from the client version`() {
        assertEquals("16001", BundledAddons.interfaceNumber("1.60.1.70374"))
        assertEquals(null, BundledAddons.interfaceNumber("garbage"))
        assertEquals(null, BundledAddons.interfaceNumber(null))
    }

    @Test
    fun `hide buff popup toc names its lua file and interface`() {
        val files = BundledAddons.files(BundledAddons.HIDE_BUFF_POPUP, "16001")
        val toc = files.getValue("HideBuffPopup.toc")
        assertTrue(toc.startsWith("## Interface: 16001\n"))
        assertTrue(toc.trimEnd().endsWith("HideBuffPopup.lua"))
        assertTrue(files.getValue("HideBuffPopup.lua").contains("BuffFrameTooltip"))
    }

    @Test
    fun `installed folder is recognised as the catalog addon`() {
        assertEquals("hidebuffpopup", Addons.catalogIdOf(Addons.Installed("HideBuffPopup", "Hide Buff Popup", null, listOf("HideBuffPopup"))))
    }
}
