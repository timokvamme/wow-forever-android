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

    @Test
    fun `finds the newest downloaded zip for an addon`() {
        val dir = Files.createTempDirectory("downloads").toFile()
        try {
            dir.resolve("Controller-Forever-1.0.zip").apply { writeText("") }.setLastModified(1_000)
            dir.resolve("ControllerForever-1.1.zip").apply { writeText("") }.setLastModified(2_000)
            dir.resolve("ConsolePort-3.3.10.zip").writeText("")
            dir.resolve("controllerforever-notes.txt").writeText("")
            assertEquals("ControllerForever-1.1.zip", Addons.findDownloadedZip(dir, "controllerforever")?.name)
            assertEquals(null, Addons.findDownloadedZip(dir, "easycontroller"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `matches saved variables of every addon folder`() {
        val addon = Addons.Installed("ConsolePort", "ConsolePort", null, listOf("ConsolePort", "ConsolePort_Bar"))
        assertTrue(Addons.isSavedVariablesOf(addon, "ConsolePort.lua"))
        assertTrue(Addons.isSavedVariablesOf(addon, "ConsolePort_Bar.lua.bak"))
        assertFalse(Addons.isSavedVariablesOf(addon, "ConsolePort_Keyboard.lua"))
        assertFalse(Addons.isSavedVariablesOf(addon, "Blizzard_Console.lua"))
    }

    @Test
    fun `recognises catalog addons by recorded id or folder name`() {
        assertEquals("leatrix-plus", Addons.catalogIdOf(Addons.Installed("Leatrix_Plus", "Leatrix Plus", null, listOf("Leatrix_Plus"))))
        assertEquals("whisper-messenger", Addons.catalogIdOf(Addons.Installed("WhisperMessenger", "Whisper", null, listOf("WhisperMessenger"))))
        assertEquals("auctioneer-forever", Addons.catalogIdOf(Addons.Installed("Auc", "Auc", null, listOf("Auc"), "auctioneer-forever")))
        assertEquals(null, Addons.catalogIdOf(Addons.Installed("GamepadLayoutRecovery", "Gamepad Bar Recovery", null, listOf("GamepadLayoutRecovery"))))
    }

    @Test
    fun `prefers files built for WoW Forever`() {
        assertTrue(Addons.isForeverFile(org.json.JSONObject().put("gameVersions", org.json.JSONArray(listOf("1.60.1")))))
        assertTrue(Addons.isForeverFile(org.json.JSONObject().put("gameVersionTypeIds", org.json.JSONArray(listOf(67408)))))
        assertFalse(Addons.isForeverFile(org.json.JSONObject().put("gameVersions", org.json.JSONArray(listOf("12.0.5")))))
    }
}
