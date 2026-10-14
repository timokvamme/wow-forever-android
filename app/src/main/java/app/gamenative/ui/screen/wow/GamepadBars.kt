package app.gamenative.ui.screen.wow

import java.io.File

/**
 * Hides Blizzard's gamepad action bars (GamepadMainActionBarFrame, the controller-layout buttons)
 * while the gamepad UI mode stays on, by writing a small addon that keeps the frame at alpha 0.
 * Alpha is not restricted in combat, so the bars stay hidden and keep working.
 */
object GamepadBars {
    const val ADDON = "WoWForever_HideGamepadBars"

    private val LUA = """
        local function hide(frame)
            if not frame or frame.wowForeverHidden then return end
            frame.wowForeverHidden = true
            frame:SetAlpha(0)
            hooksecurefunc(frame, "SetAlpha", function(self, alpha)
                if alpha and alpha > 0 then self:SetAlpha(0) end
            end)
            frame:HookScript("OnShow", function(self) self:SetAlpha(0) end)
        end

        local events = CreateFrame("Frame")
        events:RegisterEvent("ADDON_LOADED")
        events:RegisterEvent("PLAYER_ENTERING_WORLD")
        events:SetScript("OnEvent", function()
            hide(_G.GamepadMainActionBarFrame)
        end)
    """.trimIndent() + "\n"

    /** "1.60.1.70374" -> "16001", the ## Interface number the client expects. */
    internal fun interfaceNumber(version: String?): String? {
        val parts = version?.split('.')?.take(3)?.map { it.toIntOrNull() ?: return null } ?: return null
        if (parts.size < 3) return null
        return (parts[0] * 10000 + parts[1] * 100 + parts[2]).toString()
    }

    /** Installs the addon when [hide] is set and removes it otherwise. */
    fun apply(gameRoot: File, hide: Boolean) {
        val dir = File(Addons.addonsDir(gameRoot), ADDON)
        if (!hide) {
            if (dir.exists()) dir.deleteRecursively()
            return
        }
        val version = WowClientDownloader.readBuildInfo(File(gameRoot, WowClientDownloader.BUILD_INFO))?.get("Version")
        val toc = buildString {
            interfaceNumber(version)?.let { append("## Interface: $it\n") }
            append("## Title: WoW Forever: Hide Gamepad Bars\n")
            append("## Notes: Written by the WoW Forever launcher. Turn it off in the launcher's Game Settings.\n")
            append("## OptionalDeps: Blizzard_GamepadActionBars\n")
            append("\n$ADDON.lua\n")
        }
        dir.mkdirs()
        writeIfChanged(File(dir, "$ADDON.toc"), toc)
        writeIfChanged(File(dir, "$ADDON.lua"), LUA)
    }

    private fun writeIfChanged(file: File, text: String) {
        if (!file.isFile || file.readText() != text) file.writeText(text)
    }
}
