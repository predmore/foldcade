package app.foldcade

import app.foldcade.language.InstalledTheme
import app.foldcade.language.ThemeFile
import app.foldcade.language.ThemeInstallRefusal
import app.foldcade.language.ThemeLedger
import app.foldcade.language.ThemeProcessExit
import app.foldcade.language.ThemeRollback
import app.foldcade.language.ThemeTrial
import app.foldcade.language.ThemeZip
import app.foldcade.language.checkThemeInstall
import app.foldcade.language.readThemeZip
import app.foldcade.romm.writeDurably
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/** The largest archive [ThemeInstalls.install] takes. The loader holds a pack's files in memory. */
internal const val MAX_THEME_INSTALL_BYTES: Long = 128L * 1024 * 1024

sealed interface ThemeInstallResult {
    /** It takes effect the next time Foldcade starts, on trial. */
    data class Installed(val theme: InstalledTheme) : ThemeInstallResult
    data class Refused(val reason: ThemeInstallRefusal) : ThemeInstallResult
}

/**
 * The packs in `files/themes` that Foldcade did not ship, and the copies that keep them safe to
 * change. A pack that arrives or changes goes on trial. If Foldcade crashes or stops answering
 * twice while it is in use, the next start puts the copy before it back, or takes it out when
 * there was none. An update keeps the copy it replaced in `themes-previous`.
 *
 * Archives move only by rename within [root], so each move is whole. [ThemeLedger.pending] is
 * written before an update moves anything, and [begin] finishes a move that a stop interrupted.
 */
internal class ThemeInstalls(private val root: File) {
    private val themes = File(root, "themes")
    private val previous = File(root, "themes-previous")
    private val staging = File(root, "themes-staging")
    private val ledgerFile = File(root, "theme-installs.json")

    @Volatile
    var ledger: ThemeLedger = ThemeLedger()
        private set

    /**
     * Before the packs load: finishes an interrupted update, notes what arrived, counts the
     * [exits] since the last start against the packs on trial that [choices] (the saved theme,
     * icon and background choices) had in use, and puts back each pack that reached its strikes.
     */
    @Synchronized
    fun begin(exits: List<ThemeProcessExit>, choices: Set<String>): List<ThemeRollback> {
        val stored = ledgerFile.takeIf { it.isFile }?.let { file -> runCatching { file.readText() }.getOrNull() }?.let(ThemeLedger::decode)
        ledger = stored ?: ThemeLedger()
        ledger.pending?.let(::finish)
        staging.deleteRecursively()
        val present = archives()
        val before = ledger
        ledger = ledger.arrive(present, adopt = stored == null)
        // A copy kept beside an archive belongs to the update that replaced it, not to one pushed over it.
        previous.listFiles().orEmpty().forEach { kept ->
            val stamp = present[kept.name]
            if (stamp == null || (stored != null && before.theme(kept.name)?.stamp != stamp)) kept.delete()
        }
        val (struck, failed) = ledger.afterExits(exits, ledger.filesFor(choices))
        ledger = struck
        val rollbacks = failed.map(::rollBack)
        ledger = ledger.startRunning()
        write()
        return rollbacks
    }

    /** The packs loaded: each archive's name and its manifest. Loading did not stop them. */
    @Synchronized
    fun loaded(manifests: Map<String, ThemeFile>) {
        ledger = ledger.loaded(manifests)
        write()
    }

    /** [millis] more of Home shown with the packs [choices] name in use. */
    @Synchronized
    fun shown(choices: Set<String>, millis: Long) {
        if (ledger.themes.none { it.trial != null }) return
        val next = ledger.shown(ledger.filesFor(choices), millis)
        if (next == ledger) return
        ledger = next
        write()
    }

    /**
     * Installs the archive in [input] for the next start, if [checkThemeInstall] allows it and
     * [validate] accepts it. A pack already installed under the same id is replaced and its copy
     * kept. [bundled] are the ids Foldcade ships.
     */
    @Synchronized
    fun install(input: InputStream, bundled: Set<String>, validate: (ThemeZip) -> Boolean): ThemeInstallResult {
        staging.mkdirs()
        val download = File(staging, "download")
        try {
            if (!copyAtMost(input, download, MAX_THEME_INSTALL_BYTES)) return ThemeInstallResult.Refused(ThemeInstallRefusal.TooLarge)
            val zip = download.inputStream().use(::readThemeZip) ?: return ThemeInstallResult.Refused(ThemeInstallRefusal.NotATheme)
            checkThemeInstall(zip.file, ledger, bundled)?.let { return ThemeInstallResult.Refused(it) }
            if (!validate(zip)) return ThemeInstallResult.Refused(ThemeInstallRefusal.Broken)
            val existing = ledger.themes.firstOrNull { it.id == zip.file.id }
            val name = existing?.file ?: freeName(zip.file.id)
            val staged = File(staging, name)
            check(download.renameTo(staged)) { "Cannot stage $name" }
            val theme = InstalledTheme(
                file = name,
                stamp = stamp(staged),
                id = zip.file.id,
                name = zip.file.name,
                release = zip.file.release,
                source = zip.file.source,
                previous = existing?.release,
                trial = ThemeTrial(),
            )
            ledger = ledger.copy(pending = theme)
            write()
            finish(theme)
            write()
            return ThemeInstallResult.Installed(theme)
        } finally {
            download.delete()
        }
    }

    /**
     * Moves the staged [theme] into place, keeping the archive it replaces. Run again after a stop
     * part way, it carries on from where that stop left the files.
     */
    private fun finish(theme: InstalledTheme) {
        val active = File(themes, theme.file)
        val staged = File(staging, theme.file)
        val kept = File(previous, theme.file)
        if (staged.isFile && stamp(staged) == theme.stamp) {
            themes.mkdirs()
            if (active.isFile) {
                previous.mkdirs()
                check(active.renameTo(kept)) { "Cannot keep ${theme.file}" }
            }
            check(staged.renameTo(active)) { "Cannot install ${theme.file}" }
        } else if (!active.isFile && kept.isFile) {
            // The staged copy is gone, so the one it was replacing goes back.
            kept.renameTo(active)
        }
        ledger = ledger.copy(pending = null).let { if (active.isFile && stamp(active) == theme.stamp) it.withTheme(theme) else it }
    }

    private fun rollBack(file: String): ThemeRollback {
        val theme = ledger.theme(file)
        val active = File(themes, file)
        val kept = File(previous, file)
        val restored = if (kept.isFile && kept.renameTo(active)) {
            InstalledTheme(file, stamp(active), theme?.id, theme?.name, theme?.previous, theme?.source)
        } else {
            active.delete()
            null
        }
        ledger = ledger.rolledBack(file, restored)
        return ThemeRollback(file, theme?.name, theme?.release, restored != null)
    }

    private fun archives(): Map<String, String> =
        themes.listFiles().orEmpty().filter { it.isFile && it.extension == "zip" }.associate { it.name to stamp(it) }

    private fun freeName(id: String): String = generateSequence(1) { it + 1 }
        .map { if (it == 1) "$id.zip" else "$id-$it.zip" }
        .first { name -> !File(themes, name).exists() && ledger.theme(name) == null }

    private fun write() = writeDurably(ledgerFile.toPath(), ledger.encode().encodeToByteArray())

    private fun stamp(file: File): String = "${file.length()}:${file.lastModified()}"

    private fun copyAtMost(input: InputStream, target: File, max: Long): Boolean {
        var total = 0L
        FileOutputStream(target).use { out ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                if (total > max) return false
                out.write(buffer, 0, read)
            }
            // On disk before it is renamed into place, so a power cut cannot install a torn archive.
            out.fd.sync()
        }
        return true
    }
}
