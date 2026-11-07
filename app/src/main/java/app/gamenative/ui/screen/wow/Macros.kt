package app.gamenative.ui.screen.wow

import java.io.File

/**
 * Reads and writes WoW's macros-cache.txt, so macros can be edited in the launcher with the phone's
 * keyboard. Account-wide macros live in WTF/Account/<account>/, a character's in
 * WTF/Account/<account>/<realm>/<character>/. The game rewrites these files when it exits, so they
 * are only edited while it isn't running.
 */
object Macros {
    data class Macro(val id: String, val name: String, val icon: String, val body: String)

    /** Where a set of macros is saved: the account-wide list or one character's. */
    data class Owner(val label: String, val dir: File) {
        val file get() = File(dir, FILE_NAME)
    }

    const val FILE_NAME = "macros-cache.txt"
    const val MAX_NAME = 16
    const val MAX_BODY = 255
    private const val QUESTION_MARK_ICON = "INV_MISC_QUESTIONMARK"

    /** Account-wide and per-character macro owners found under the game's WTF folder. */
    fun owners(gameRoot: File): List<Owner> {
        val accounts = File(gameRoot, "${WowClientDownloader.FLAVOR_DIR}/WTF/Account").listFiles().orEmpty()
            .filter { it.isDirectory && it.name != "SavedVariables" }
            .sortedBy { it.name }
        val multipleAccounts = accounts.size > 1
        return accounts.flatMap { account ->
            val prefix = if (multipleAccounts) "${account.name}: " else ""
            val characters = account.listFiles().orEmpty()
                .filter { it.isDirectory && it.name != "SavedVariables" }
                .flatMap { realm ->
                    realm.listFiles().orEmpty()
                        .filter { it.isDirectory && it.name != "SavedVariables" }
                        .map { Owner("$prefix${it.name} (${realm.name})", it) }
                }
                .sortedBy { it.label.lowercase() }
            characters + Owner("${prefix}All characters (account macros)", account)
        }
    }

    fun parse(text: String): List<Macro> {
        val macros = mutableListOf<Macro>()
        var header: List<String>? = null
        val body = StringBuilder()
        text.lines().forEach { line ->
            val current = header
            when {
                current == null && line.startsWith("VER ") -> {
                    header = Regex("\"([^\"]*)\"|(\\S+)").findAll(line).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList()
                    body.clear()
                }
                current != null && line == "END" -> {
                    macros += Macro(
                        id = current.getOrElse(2) { "" },
                        name = current.getOrElse(3) { "" },
                        icon = current.getOrElse(4) { QUESTION_MARK_ICON },
                        body = body.toString().trimEnd('\n'),
                    )
                    header = null
                }
                current != null -> body.append(line).append('\n')
            }
        }
        return macros
    }

    fun serialize(macros: List<Macro>): String = buildString {
        macros.forEach { m ->
            append("VER 3 ${m.id} \"${m.name.replace("\"", "")}\" \"${m.icon}\"\n")
            m.body.lines().forEach { append(it.trimEnd('\r')).append('\n') }
            append("END\n")
        }
    }

    fun load(owner: Owner): List<Macro> = if (owner.file.isFile) parse(owner.file.readText()) else emptyList()

    fun save(owner: Owner, macros: List<Macro>) {
        owner.dir.mkdirs()
        owner.file.writeText(serialize(macros))
    }

    /** A new macro with an id no other macro in [existing] uses. */
    fun newMacro(existing: List<Macro>, name: String, body: String): Macro {
        val next = (existing.mapNotNull { it.id.toLongOrNull(16) }.maxOrNull() ?: 0L) + 1
        return Macro("%016X".format(next), name, QUESTION_MARK_ICON, body)
    }

    /**
     * Turns off macro syncing in Config.wtf. With it on, the game replaces the local macros with the
     * copy saved on Blizzard's servers at login, which would undo edits made here.
     */
    fun keepLocalMacros(gameRoot: File) {
        val config = File(gameRoot, "${WowClientDownloader.FLAVOR_DIR}/WTF/Config.wtf")
        if (!config.isFile) return
        val lines = config.readLines()
        val updated = GameSettings.applyOverrides(lines, mapOf("synchronizeMacros" to "0"))
        if (updated != lines) config.writeText(updated.joinToString("\n") + "\n")
    }
}
