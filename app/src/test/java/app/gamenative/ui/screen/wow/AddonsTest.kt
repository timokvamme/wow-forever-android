package app.gamenative.ui.screen.wow

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AddonsTest {
    @Test
    fun `recognises flavor tocs named after the folder`() {
        assertTrue(Addons.isOwnToc("ConsolePort", "ConsolePort.toc"))
        assertTrue(Addons.isOwnToc("ConsolePort", "ConsolePort_Mainline.toc"))
        assertTrue(Addons.isOwnToc("ConsolePort", "ConsolePort-Classic.toc"))
        assertFalse(Addons.isOwnToc("ConsolePort", "ConsolePort_Bar.lua"))
        assertFalse(Addons.isOwnToc("ConsolePort", "Other.toc"))
    }

    @Test
    fun `strips color and texture escapes from titles`() {
        assertEquals("ConsolePort Bar", Addons.cleanTitle(" |cffff6600ConsolePort|r Bar |TInterface\\Icon:16|t"))
    }

    @Test
    fun `finds addon folders inside a wrapper folder`() {
        val root = Files.createTempDirectory("addons").toFile()
        try {
            root.resolve("pack/ConsolePort").mkdirs()
            root.resolve("pack/ConsolePort/ConsolePort.toc").writeText("## Title: ConsolePort")
            root.resolve("pack/ConsolePort/Libs/Lib").mkdirs()
            root.resolve("pack/ConsolePort/Libs/Lib/Lib.toc").writeText("")
            root.resolve("pack/ConsolePort_Bar").mkdirs()
            root.resolve("pack/ConsolePort_Bar/ConsolePort_Bar_Mainline.toc").writeText("")
            root.resolve("README").writeText("")
            assertEquals(listOf("ConsolePort", "ConsolePort_Bar"), Addons.findAddonDirs(root).map { it.name }.sorted())
        } finally {
            root.deleteRecursively()
        }
    }
}
