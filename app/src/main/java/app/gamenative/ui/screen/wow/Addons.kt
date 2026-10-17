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
import timber.log.Timber

/**
 * Installs and removes WoW addons in <game>/_classic_beta_/Interface/AddOns. An addon zip often
 * holds several folders (ConsolePort ships ConsolePort, ConsolePort_Bar, ...), so each install is
 * recorded as a package in Interface/wowforever-addons.json and removed as one.
 */
object Addons {
    data class Installed(
        val name: String,
        val title: String,
        val version: String?,
        val folders: List<String>,
        val catalogId: String? = null,
    )

    /** An addon the launcher offers by name. [slug] is its CurseForge page; null means ConsolePort's GitHub releases. */
    data class CatalogAddon(
        val id: String,
        val title: String,
        val description: String,
        val slug: String?,
        val installByDefault: Boolean,
    ) {
        val downloadPage get() = "https://www.curseforge.com/wow/addons/$slug/download"
    }

    val CATALOG = listOf(
        CatalogAddon("leatrix-plus", "Leatrix Plus", "Quality-of-life tweaks", "leatrix-plus", true),
        CatalogAddon("decursive", "Decursive", "Dispel helper", "decursive", true),
        CatalogAddon("moveany", "MoveAny", "Move any UI element", "moveany", true),
        CatalogAddon("syndicator", "Syndicator", "Item data, needed by Baganator", "syndicator", true),
        CatalogAddon("baganator", "Baganator", "Bags and bank", "baganator", true),
        CatalogAddon("whisper-messenger", "Whisper Messenger", "Whisper conversations window", "whisper-messenger", true),
        CatalogAddon("enhancedquesttracker", "Enhanced Quest Tracker", "Quests grouped by zone", "enhancedquesttracker", true),
        CatalogAddon("auctioneer-forever", "Auctioneer (Forever)", "Auction house scanning and posting", "auctioneer-forever", true),
        CatalogAddon(CONSOLEPORT_ID, "ConsolePort", "Controller UI", null, false),
        CatalogAddon("controllerforever", "ControllerForever", "Controller extras", "controllerforever", false),
    )

    private const val MANIFEST = "wowforever-addons.json"
    private const val CONSOLEPORT_RELEASE = "https://api.github.com/repos/seblindfors/ConsolePort/releases/latest"
    const val CONSOLEPORT = "ConsolePort"
    private const val CONSOLEPORT_ID = "consoleport"
    private const val DEFAULTS_FILE = "wowforever-addon-defaults.txt"

    /** CurseForge's game version type for WoW Forever; its files are preferred over other flavors. */
    private const val FOREVER_VERSION_TYPE = 67408
    private const val CURSEFORGE_API = "https://www.curseforge.com/api/v1/mods"
    private const val BROWSER_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Mobile Safari/537.36"

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
            Installed(
                name, tocTitle(File(dir, name)) ?: name, entry.optString("version").ifBlank { null }, folders,
                entry.optString("catalog").ifBlank { null },
            )
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

    /**
     * Deletes the addon's saved variables for every account and character, so it starts with its
     * defaults. Returns how many files were removed. The game must not be running.
     */
    fun resetSettings(gameRoot: File, addon: Installed): Int {
        val accounts = File(gameRoot, "${WowClientDownloader.FLAVOR_DIR}/WTF/Account")
        return accounts.walkTopDown().maxDepth(5)
            .filter { it.isFile && it.parentFile?.name == "SavedVariables" && isSavedVariablesOf(addon, it.name) }
            .toList()
            .count { it.delete() }
    }

    internal fun isSavedVariablesOf(addon: Installed, file: String): Boolean =
        addon.folders.any { file.equals("$it.lua", ignoreCase = true) || file.equals("$it.lua.bak", ignoreCase = true) }

    /** Installs every addon folder found in [zip]; returns the package that was recorded. */
    fun installZip(gameRoot: File, zip: InputStream, workDir: File, version: String? = null, catalogId: String? = null): Installed {
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
            // A catalog addon keeps one package even if a new version's shortest folder name changed.
            if (catalogId != null) {
                manifest.keys().asSequence().toList()
                    .filter { it != name && manifest.optJSONObject(it)?.optString("catalog") == catalogId }
                    .forEach { old ->
                        manifest.optJSONObject(old)?.optJSONArray("folders").toStringList()
                            .filter { it !in folders }
                            .forEach { File(dir, it).deleteRecursively() }
                        manifest.remove(old)
                    }
            }
            manifest.put(
                name,
                JSONObject().put("folders", JSONArray(folders)).put("version", version ?: "").put("catalog", catalogId ?: ""),
            )
            writeManifest(gameRoot, manifest)
            return Installed(name, tocTitle(File(dir, name)) ?: name, version, folders, catalogId)
        } finally {
            staging.deleteRecursively()
        }
    }

    /** Downloads the latest ConsolePort release from its GitHub page and installs it. */
    private fun installConsolePort(gameRoot: File, workDir: File, onProgress: (Float) -> Unit): Installed {
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
            download(Request.Builder().url(url).build(), zip, onProgress)
            return zip.inputStream().use { installZip(gameRoot, it, workDir, release.optString("tag_name"), CONSOLEPORT_ID) }
        } finally {
            zip.delete()
        }
    }

    /** The catalog addon an installed package came from, recorded or recognised by folder name. */
    fun catalogIdOf(addon: Installed): String? =
        addon.catalogId ?: CATALOG.firstOrNull { c ->
            addon.folders.any { f -> f.lowercase().filter(Char::isLetterOrDigit) == c.id.filter(Char::isLetterOrDigit) }
        }?.id

    /**
     * Installs [addon] from its source, or from a matching zip already in [downloadsDir]. Returns
     * null when neither works, so the caller can open the CurseForge page for a browser download;
     * CurseForge may refuse requests that don't come from a browser.
     */
    suspend fun installCatalog(
        addon: CatalogAddon,
        gameRoot: File,
        workDir: File,
        downloadsDir: File?,
        onProgress: (Float) -> Unit,
    ): Installed? = withContext(Dispatchers.IO) { installCatalogBlocking(addon, gameRoot, workDir, downloadsDir, onProgress) }

    private fun installCatalogBlocking(
        addon: CatalogAddon,
        gameRoot: File,
        workDir: File,
        downloadsDir: File?,
        onProgress: (Float) -> Unit,
    ): Installed? {
        val slug = addon.slug ?: return installConsolePort(gameRoot, workDir, onProgress)
        return runCatching { installFromCurseForge(addon, gameRoot, workDir, onProgress) }
            .onFailure { Timber.w(it, "CurseForge download of %s failed", slug) }
            .getOrNull()
            ?: downloadsDir?.let { findDownloadedZip(it, slug) }?.let { zip ->
                Timber.i("Installing %s from %s", slug, zip)
                zip.inputStream().use {
                    installZip(gameRoot, it, workDir, zip.nameWithoutExtension.substringAfter('-', "").ifBlank { null }, addon.id)
                }
            }
    }

    /**
     * Installs each default catalog addon once per game folder. An addon the player removed later
     * is not put back. Returns the titles that could not be downloaded.
     */
    fun installDefaults(gameRoot: File, workDir: File, onStatus: (String) -> Unit): List<String> {
        val offeredFile = File(gameRoot, "${WowClientDownloader.FLAVOR_DIR}/Interface/$DEFAULTS_FILE")
        val offered = runCatching { offeredFile.readLines().map(String::trim).toMutableSet() }.getOrDefault(mutableSetOf())
        val installedIds = list(gameRoot).mapNotNull(::catalogIdOf).toSet()
        val failed = mutableListOf<String>()
        CATALOG.filter { it.installByDefault && it.id !in offered }.forEach { addon ->
            if (addon.id !in installedIds) {
                onStatus("Installing ${addon.title}...")
                if (runCatching { installCatalogBlocking(addon, gameRoot, workDir, null) {} }.getOrNull() == null) {
                    failed += addon.title
                }
            }
            offered += addon.id
        }
        offeredFile.parentFile?.mkdirs()
        offeredFile.writeText(offered.sorted().joinToString("\n") + "\n")
        return failed
    }

    private fun installFromCurseForge(addon: CatalogAddon, gameRoot: File, workDir: File, onProgress: (Float) -> Unit): Installed {
        val slug = checkNotNull(addon.slug)
        val mod = getJson("$CURSEFORGE_API/search?gameId=1&index=0&pageSize=20&filterText=$slug").optJSONArray("data").objects()
            .firstOrNull { it.optString("slug").equals(slug, ignoreCase = true) }
            ?: error("$slug not found on CurseForge")
        val modId = mod.getLong("id")
        val zips = getJson("$CURSEFORGE_API/$modId/files?pageIndex=0&pageSize=50&sort=dateCreated&sortDescending=true")
            .optJSONArray("data").objects()
            .filter { it.optString("fileName").endsWith(".zip", ignoreCase = true) }
        val file = zips.firstOrNull(::isForeverFile) ?: zips.firstOrNull() ?: error("No files for $slug on CurseForge")
        val zip = File(workDir, "$slug.zip")
        try {
            download("$CURSEFORGE_API/$modId/files/${file.getLong("id")}/download", zip, onProgress)
            val version = file.optString("displayName").ifBlank { file.optString("fileName") }.removeSuffix(".zip")
            return zip.inputStream().use { installZip(gameRoot, it, workDir, version, addon.id) }
        } finally {
            zip.delete()
        }
    }

    /** A CurseForge file built for WoW Forever (game version 1.60 and later 1.x). */
    internal fun isForeverFile(file: JSONObject): Boolean {
        val typeIds = file.optJSONArray("gameVersionTypeIds")
        if (typeIds != null && (0 until typeIds.length()).any { typeIds.optInt(it) == FOREVER_VERSION_TYPE }) return true
        if (file.optInt("gameVersionTypeId") == FOREVER_VERSION_TYPE) return true
        val versions = file.optJSONArray("gameVersions").toStringList()
        return versions.any { it.startsWith("1.6") }
    }

    /** Newest zip in [dir] whose name, ignoring case and punctuation, contains [slug]. */
    internal fun findDownloadedZip(dir: File, slug: String): File? {
        val key = slug.lowercase().filter(Char::isLetterOrDigit)
        return dir.listFiles().orEmpty()
            .filter { it.isFile && it.extension.equals("zip", ignoreCase = true) }
            .filter { key in it.name.lowercase().filter(Char::isLetterOrDigit) }
            .maxByOrNull { it.lastModified() }
    }

    /**
     * Installs a zip that a page in the in-app browser started downloading, sending the browser's
     * user agent and cookies so sites behind a bot check (CurseForge) serve the file.
     */
    suspend fun installFromBrowserDownload(
        gameRoot: File,
        workDir: File,
        url: String,
        userAgent: String,
        cookies: String?,
        fileName: String,
        catalogId: String?,
        onProgress: (Float) -> Unit,
    ): Installed = withContext(Dispatchers.IO) {
        val zip = File(workDir, "browser-download.zip")
        try {
            val request = Request.Builder().url(url).header("User-Agent", userAgent)
                .apply { if (!cookies.isNullOrBlank()) header("Cookie", cookies) }
                .build()
            download(request, zip, onProgress)
            val version = fileName.removeSuffix(".zip").substringAfter('-', "").ifBlank { null }
            zip.inputStream().use { installZip(gameRoot, it, workDir, version, catalogId) }
        } finally {
            zip.delete()
        }
    }

    private fun browserRequest(url: String) = Request.Builder().url(url).header("User-Agent", BROWSER_UA).build()

    private fun getJson(url: String): JSONObject =
        Net.http.newCall(browserRequest(url)).execute().use { rsp ->
            check(rsp.isSuccessful) { "CurseForge returned HTTP ${rsp.code}" }
            JSONObject(rsp.body!!.string())
        }

    private fun download(url: String, dest: File, onProgress: (Float) -> Unit) = download(browserRequest(url), dest, onProgress)

    private fun download(request: Request, dest: File, onProgress: (Float) -> Unit) {
        Net.http.newCall(request).execute().use { rsp ->
            check(rsp.isSuccessful) { "Download returned HTTP ${rsp.code}" }
            val body = rsp.body!!
            val total = body.contentLength()
            dest.outputStream().use { out ->
                val input = body.byteStream()
                val buf = ByteArray(8 * 1024)
                var read = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    read += n
                    if (total > 0) onProgress(read.toFloat() / total)
                }
            }
        }
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

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
