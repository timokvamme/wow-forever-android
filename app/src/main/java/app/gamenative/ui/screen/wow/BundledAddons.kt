package app.gamenative.ui.screen.wow

import java.io.File

/** Small addons that ship inside the launcher instead of being downloaded. */
object BundledAddons {
    /** Hides the gamepad buff popup (BuffFrameTooltip) for the listed buffs, or for all with HIDE_ALL. */
    const val HIDE_BUFF_POPUP = "HideBuffPopup"

    private val HIDE_BUFF_POPUP_LUA = """
        local HIDE_ALL = false
        local BLOCK = {
          ["Plainsrunning"] = true,
          -- ["Cozy Fire"] = true,
        }

        local function hasBlockedText(frame)
          for _, r in ipairs({ frame:GetRegions() }) do
            if r.GetText then
              local t = r:GetText()
              if t and BLOCK[t] then return true end
            end
          end
          for _, child in ipairs({ frame:GetChildren() }) do
            if hasBlockedText(child) then return true end
          end
          return false
        end

        local function check(tt)
          if HIDE_ALL or hasBlockedText(tt) then tt:Hide() end
        end

        local function hook()
          local tt = _G.BuffFrameTooltip
          if not tt or tt.__hbpHooked then return end
          tt.__hbpHooked = true
          tt:HookScript("OnShow", function(self)
            check(self)
            -- the text is sometimes filled in a frame later
            C_Timer.After(0, function() if self:IsShown() then check(self) end end)
          end)
        end

        local f = CreateFrame("Frame")
        f:RegisterEvent("PLAYER_LOGIN")
        f:SetScript("OnEvent", hook)
        hook()
    """.trimIndent() + "\n"

    /** Files of the bundled addon [folder], keyed by file name, for a client of [interfaceNumber]. */
    fun files(folder: String, interfaceNumber: String?): Map<String, String> = when (folder) {
        HIDE_BUFF_POPUP -> mapOf(
            "$folder.toc" to buildString {
                interfaceNumber?.let { append("## Interface: $it\n") }
                append("## Title: Hide Buff Popup\n")
                append("## Notes: Hides the gamepad buff popup (BuffFrameTooltip)\n")
                append("\n$folder.lua\n")
            },
            "$folder.lua" to HIDE_BUFF_POPUP_LUA,
        )
        else -> error("No bundled addon $folder")
    }

    /** "1.60.1.70374" -> "16001", the ## Interface number the client expects. */
    internal fun interfaceNumber(version: String?): String? {
        val parts = version?.split('.')?.take(3)?.map { it.toIntOrNull() ?: return null } ?: return null
        if (parts.size < 3) return null
        return (parts[0] * 10000 + parts[1] * 100 + parts[2]).toString()
    }

    fun interfaceNumberOf(gameRoot: File): String? =
        interfaceNumber(WowClientDownloader.readBuildInfo(File(gameRoot, WowClientDownloader.BUILD_INFO))?.get("Version"))
}
