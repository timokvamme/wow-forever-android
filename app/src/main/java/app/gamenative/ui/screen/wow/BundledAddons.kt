package app.gamenative.ui.screen.wow

import java.io.File

/** Small addons that ship inside the launcher instead of being downloaded. */
object BundledAddons {
    /** A launcher-made addon; its Lua lives in resources/bundled_addons/<folder>.lua. */
    private data class Addon(val folder: String, val title: String, val notes: String)

    const val HIDE_BUFF_POPUP = "HideBuffPopup"
    const val COMPACT_CHAT = "CompactChat"
    const val COMPACT_QUESTS = "CompactQuests"
    const val BUFF_REMINDER = "BuffReminder"

    private val ADDONS = listOf(
        Addon(HIDE_BUFF_POPUP, "Hide Buff Popup", "Hides the gamepad buff popup (BuffFrameTooltip)"),
        Addon(COMPACT_CHAT, "Compact Chat", "Short channel tags, short system lines, class colors, chat into the corners"),
        Addon(COMPACT_QUESTS, "Compact Quests", "Smaller quest tracker that tracks the nearest and most active quests"),
        Addon(BUFF_REMINDER, "Buff Reminder", "Shows an icon above your character when Battle Shout or Lightning Shield is missing"),
    )

    /** Files of the bundled addon [folder], keyed by file name, for a client of [interfaceNumber]. */
    fun files(folder: String, interfaceNumber: String?): Map<String, String> {
        val addon = ADDONS.firstOrNull { it.folder == folder } ?: error("No bundled addon $folder")
        val lua = BundledAddons::class.java.getResourceAsStream("/bundled_addons/$folder.lua")
            ?.use { it.readBytes().decodeToString() }
            ?: error("Missing bundled_addons/$folder.lua")
        return mapOf(
            "$folder.toc" to buildString {
                interfaceNumber?.let { append("## Interface: $it\n") }
                append("## Title: ${addon.title}\n")
                append("## Notes: ${addon.notes}\n")
                append("\n$folder.lua\n")
            },
            "$folder.lua" to lua,
        )
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
