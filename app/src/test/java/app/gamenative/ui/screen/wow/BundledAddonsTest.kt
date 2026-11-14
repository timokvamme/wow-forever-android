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

    @Test
    fun `every bundled addon in the catalog has its lua`() {
        Addons.CATALOG.mapNotNull { it.bundled }.forEach { folder ->
            val files = BundledAddons.files(folder, "16001")
            assertTrue(folder, files.getValue("$folder.lua").length > 100)
            assertTrue(folder, files.getValue("$folder.toc").contains("$folder.lua"))
            assertTrue(folder, files.getValue("$folder.toc").contains("## SavedVariables: ${folder}DB"))
            assertTrue(folder, files.getValue("$folder.lua").startsWith("-- Shared by the launcher's addons"))
        }
    }

    @Test
    fun `no bundled addon file starts with an underscore, which the APK build drops`() {
        val dir = java.io.File("src/main/resources/bundled_addons")
        val names = dir.list().orEmpty().toList()
        assertTrue("found no files in ${dir.absolutePath}", names.isNotEmpty())
        assertTrue(names.toString(), names.none { it.startsWith("_") })
    }
}
