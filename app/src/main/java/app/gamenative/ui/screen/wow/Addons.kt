package app.gamenative.ui.screen.wow

import app.gamenative.utils.Net
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

/**
 * Installs and removes WoW addons in <game>/_classic_beta_/Interface/AddOns. An addon zip often
 * holds several folders (ConsolePort ships ConsolePort, ConsolePort_Bar, ...), so each install is
 * recorded as a package in Interface/wowforever-addons.json and removed as one.
 */
object Addons {
    data class Installed(val name: String, val title: String, val version: String?, val folders: List<String>)

    private const val MANIFEST = "wowforever-addons.json"
    private const val CONSOLEPORT_RELEASE = "https://api.github.com/repos/seblindfors/ConsolePort/releases/latest"
    const val CONSOLEPORT = "ConsolePort"

    fun addonsDir(gameRoot: File) = File(gameRoot, "${WowClientDownloader.FLAVOR_DIR}/Interface/AddOns")

    private fun manifestFile(gameRoot: File) = File(gameRoot, "${WowClientDownloader.FLAVOR_DIR}/Interface/$MANIFEST")

    private fun readManifest(gameRoot: File): JSONObject =
        runCatching { JSONObject(manifestFile(gameRoot).readText()) }.getOrDefault(JSONObject())

    private fun writeManifest(gameRoot: File, manifest: JSONObject) {
        manifestFile(gameRoot).apply { parentFile?.mkdirs() }.writeText(manifest.toString(2))
    }

    /** Installed packages first, then addon folders that were copied in by hand. */
    fun list(gameRoot: File): List<Installed> {
        val dir = addonsDir(gameRoot)
        val manifest = readManifest(gameRoot)
        val managed = mutableSetOf<String>()
        val packages = manifest.keys().asSequence().mapNotNull { name ->
            val entry = manifest.optJSONObject(name) ?: return@mapNotNull null
            val folders = entry.optJSONArray("folders").toStringList().filter { File(dir, it).isDirectory }
            managed += folders
            if (folders.isEmpty()) return@mapNotNull null
            Installed(name, tocTitle(File(dir, name)) ?: name, entry.optString("version").ifBlank { null }, folders)
        }.toList()
        val loose = dir.listFiles().orEmpty()
            .filter { it.isDirectory && it.name !in managed && !it.name.startsWith("Blizzard_") && hasToc(it) }
            .map { Installed(it.name, tocTitle(it) ?: it.name, null, listOf(it.name)) }
        return (packages + loose).sortedBy { it.title.lowercase() }
    }

    fun isInstalled(gameRoot: File, folder: String) = File(addonsDir(gameRoot), folder).isDirectory

    fun remove(gameRoot: File, addon: Installed) {
        val dir = addonsDir(gameRoot)
        addon.folders.forEach { File(dir, it).deleteRecursively() }
        val manifest = readManifest(gameRoot)
        if (manifest.has(addon.name)) {
            manifest.remove(addon.name)
            writeManifest(gameRoot, manifest)
        }
    }

    /** Installs every addon folder found in [zip]; returns the package that was recorded. */
    fun installZip(gameRoot: File, zip: InputStream, workDir: File, version: String? = null): Installed {
        val staging = File(workDir, "addon-staging").apply { deleteRecursively(); mkdirs() }
        try {
            extract(zip, staging)
            val found = findAddonDirs(staging)
            check(found.isNotEmpty()) { "No addon found in the zip (no .toc file)." }
            val dir = addonsDir(gameRoot).apply { mkdirs() }
            found.forEach { src ->
                val target = File(dir, src.name)
                target.deleteRecursively()
                check(src.copyRecursively(target, overwrite = true)) { "Couldn't copy ${src.name}" }
            }
            val folders = found.map { it.name }.sorted()
            val name = folders.minBy { it.length }
            val manifest = readManifest(gameRoot)
            // A reinstall replaces the old package, including folders the new version dropped.
            manifest.optJSONObject(name)?.optJSONArray("folders").toStringList()
                .filter { it !in folders }
                .forEach { File(dir, it).deleteRecursively() }
            manifest.put(name, JSONObject().put("folders", JSONArray(folders)).put("version", version ?: ""))
            writeManifest(gameRoot, manifest)
            return Installed(name, tocTitle(File(dir, name)) ?: name, version, folders)
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Downloads the latest ConsolePort release from its GitHub page and installs it. */
    suspend fun installConsolePort(gameRoot: File, workDir: File, onProgress: (Float) -> Unit): Installed = withContext(Dispatchers.IO) {
        val release = Net.http.newCall(Request.Builder().url(CONSOLEPORT_RELEASE).build()).execute().use { rsp ->
            check(rsp.isSuccessful) { "GitHub returned HTTP ${rsp.code}" }
            JSONObject(rsp.body!!.string())
        }
        val assets = release.optJSONArray("assets") ?: JSONArray()
        val url = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").let { n -> n.startsWith(CONSOLEPORT) && n.endsWith(".zip") } }
            ?.optString("browser_download_url")
            ?: error("No ConsolePort zip in the latest release")
        val zip = File(workDir, "consoleport.zip")
        try {
            Net.fetchFile(url, zip, onProgress)
            zip.inputStream().use { installZip(gameRoot, it, workDir, release.optString("tag_name")) }
        } finally {
            zip.delete()
        }
    }

    private fun extract(zip: InputStream, dest: File) {
        val root = dest.canonicalPath + File.separator
        ZipInputStream(zip.buffered()).use { zin ->
            while (true) {
                val entry = zin.nextEntry ?: break
                val out = File(dest, entry.name)
                check(out.canonicalPath.startsWith(root)) { "Bad path in zip: ${entry.name}" }
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zin.copyTo(it) }
                }
            }
        }
    }

    /** Folders holding a .toc named after the folder (Name.toc, Name_Mainline.toc, Name-Classic.toc). */
    internal fun findAddonDirs(root: File): List<File> {
        val result = mutableListOf<File>()
        fun walk(dir: File) {
            val children = dir.listFiles().orEmpty()
            if (dir != root && children.any { it.isFile && isOwnToc(dir.name, it.name) }) {
                result += dir
                return
            }
            children.filter { it.isDirectory && !it.name.startsWith("__MACOSX") }.forEach(::walk)
        }
        walk(root)
        return result
    }

    internal fun isOwnToc(folder: String, file: String): Boolean {
        if (!file.endsWith(".toc", ignoreCase = true)) return false
        val base = file.dropLast(4)
        return base.equals(folder, ignoreCase = true) ||
            base.startsWith("${folder}_", ignoreCase = true) ||
            base.startsWith("$folder-", ignoreCase = true)
    }

    private fun hasToc(dir: File) = dir.listFiles().orEmpty().any { it.isFile && isOwnToc(dir.name, it.name) }

    private fun tocTitle(dir: File): String? {
        val toc = dir.listFiles().orEmpty().filter { it.isFile && isOwnToc(dir.name, it.name) }.minByOrNull { it.name.length }
            ?: return null
        val line = runCatching { toc.useLines { l -> l.firstOrNull { it.startsWith("## Title:", ignoreCase = true) } } }.getOrNull()
            ?: return null
        return cleanTitle(line.substringAfter(':')).ifBlank { null }
    }

    /** Strips WoW color and texture escapes (|cffRRGGBB, |r, |T...|t) from a .toc title. */
    internal fun cleanTitle(raw: String): String =
        raw.replace(Regex("\\|c[0-9a-fA-F]{8}"), "")
            .replace("|r", "")
            .replace(Regex("\\|T[^|]*\\|t"), "")
            .trim()

    private fun JSONArray?.toStringList(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }
}
