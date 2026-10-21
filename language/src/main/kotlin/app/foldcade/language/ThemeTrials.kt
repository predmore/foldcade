package app.foldcade.language

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Abnormal exits while a pack on trial is in use before it goes back. */
const val THEME_TRIAL_STRIKES: Int = 2

/** How long a pack on trial must be in use on a shown Home before its trial ends. */
const val THEME_TRIAL_SHOWN_MILLIS: Long = 3 * 60_000L

/**
 * A pack that arrived or changed and has not yet proved itself. [strikes] counts the abnormal exits
 * while it was in use; [shownMillis] the time it was in use on a shown Home.
 */
data class ThemeTrial(val strikes: Int = 0, val shownMillis: Long = 0)

/**
 * One archive in `files/themes`, as the host last saw it. [stamp] is its size and modification
 * time: a different stamp means a different file. The manifest fields are null until it loads.
 */
data class InstalledTheme(
    val file: String,
    val stamp: String,
    val id: String? = null,
    val name: String? = null,
    val release: ThemeRelease? = null,
    val source: ThemeSource? = null,
    /** The release of the copy kept beside it, when an update replaced one. */
    val previous: ThemeRelease? = null,
    val trial: ThemeTrial? = null,
) {
    /** True when the saved choice [choice], a pack id or an old display name, means this pack. */
    fun answers(choice: String): Boolean = choice == id || (choice == name && name != null)
}

/** An archive that went back: to the copy before it ([restored]), or out of `files/themes`. */
data class ThemeRollback(val file: String, val name: String?, val release: ThemeRelease?, val restored: Boolean)

/** An exit of Foldcade's own process that Android recorded. Crashes and ANRs are [abnormal]. */
data class ThemeProcessExit(val timestampMillis: Long, val abnormal: Boolean)

/**
 * What the host knows about the archives it did not ship. [pending] is an update half written:
 * it is set before the archives move, so a start that finds it can finish or undo the move.
 * [loading] holds the archives on trial a start was loading; still set at the next start, it means
 * that start died loading them. [running] is each archive's stamp as the running process loaded
 * it, so an update installed while it runs is not blamed for what the old copy does.
 */
data class ThemeLedger(
    val themes: List<InstalledTheme> = emptyList(),
    /** Releases that went back, by pack id. An update never installs one of them again. */
    val refused: Map<String, Set<ThemeRelease>> = emptyMap(),
    val loading: Set<String> = emptySet(),
    val running: Map<String, String> = emptyMap(),
    val pending: InstalledTheme? = null,
    /** The newest exit already counted, so no exit counts twice. */
    val lastExitMillis: Long = 0,
) {
    fun theme(file: String): InstalledTheme? = themes.firstOrNull { it.file == file }

    fun withTheme(theme: InstalledTheme): ThemeLedger = copy(themes = themes.filter { it.file != theme.file } + theme)

    /**
     * The archives as they are now. One that is new or changed goes on trial, and one that left is
     * forgotten. [adopt] takes them as they are, for the first start that keeps a ledger: packs that
     * were already working are not on trial.
     */
    fun arrive(present: Map<String, String>, adopt: Boolean = false): ThemeLedger = copy(
        themes = present.map { (file, stamp) ->
            val known = theme(file)
            when {
                known?.stamp == stamp -> known
                adopt -> InstalledTheme(file, stamp)
                // Someone else replaced it, so the copy beside it is not the one that ran before.
                else -> InstalledTheme(file, stamp, trial = ThemeTrial())
            }
        }.sortedBy { it.file },
    )

    /** True when the running process loaded [theme] as it is now. */
    private fun runs(theme: InstalledTheme): Boolean = running[theme.file] == theme.stamp

    /**
     * Counts [exits] newer than the last one counted against every pack on trial that the exiting
     * process was using: those in [exposed], and every pack it was still loading. Returns the
     * archives that now have [THEME_TRIAL_STRIKES]. Call it before [startRunning].
     */
    fun afterExits(exits: List<ThemeProcessExit>, exposed: Set<String>): Pair<ThemeLedger, List<String>> {
        val fresh = exits.filter { it.timestampMillis > lastExitMillis }
        val abnormal = fresh.count { it.abnormal }
        val newest = fresh.maxOfOrNull { it.timestampMillis } ?: lastExitMillis
        val struck = themes.map { theme ->
            val trial = theme.trial
            if (trial == null || abnormal == 0 || !runs(theme) || (theme.file !in exposed && theme.file !in loading)) theme
            else theme.copy(trial = trial.copy(strikes = trial.strikes + abnormal))
        }
        val failed = struck.filter { (it.trial?.strikes ?: 0) >= THEME_TRIAL_STRIKES }.map { it.file }
        return copy(themes = struck, lastExitMillis = newest) to failed
    }

    /** The archives in use: the saved choices that name a pack. */
    fun filesFor(choices: Set<String>): Set<String> =
        themes.filter { theme -> choices.any(theme::answers) }.map { it.file }.toSet()

    /** [millis] more of Home shown with [files] in use. A trial that reached its time ends. */
    fun shown(files: Set<String>, millis: Long): ThemeLedger = copy(
        themes = themes.map { theme ->
            val trial = theme.trial
            if (trial == null || theme.file !in files || !runs(theme)) theme
            else (trial.shownMillis + millis).let { total ->
                theme.copy(trial = if (total >= THEME_TRIAL_SHOWN_MILLIS) null else trial.copy(shownMillis = total))
            }
        },
    )

    /** This process loads every archive as it is now, starting with those on trial. */
    fun startRunning(): ThemeLedger = copy(
        loading = themes.filter { it.trial != null }.map { it.file }.toSet(),
        running = themes.associate { it.file to it.stamp },
    )

    /** Loading finished, and each archive in [manifests] loaded with that manifest. */
    fun loaded(manifests: Map<String, ThemeFile>): ThemeLedger = copy(
        loading = emptySet(),
        themes = themes.map { theme ->
            manifests[theme.file]?.let { theme.copy(id = it.id, name = it.name, release = it.release, source = it.source) } ?: theme
        },
    )

    /** [file] went back. Its release is refused from now on; [restored] is the copy now in its place. */
    fun rolledBack(file: String, restored: InstalledTheme?): ThemeLedger {
        val theme = theme(file) ?: return this
        val refusedNow = theme.id?.let { id ->
            theme.release?.let { release -> refused + (id to (refused[id].orEmpty() + release)) }
        } ?: refused
        val rest = themes.filter { it.file != file }
        return copy(themes = (rest + listOfNotNull(restored)).sortedBy { it.file }, refused = refusedNow)
    }

    fun encode(): String = buildJsonObject {
        put("themes", buildJsonArray { themes.forEach { add(encodeTheme(it)) } })
        put("refused", buildJsonObject {
            refused.forEach { (id, releases) -> put(id, buildJsonArray { releases.sorted().forEach { add(JsonPrimitive(it.toString())) } }) }
        })
        put("loading", buildJsonArray { loading.sorted().forEach { add(JsonPrimitive(it)) } })
        put("running", buildJsonObject { running.toSortedMap().forEach { (file, stamp) -> put(file, stamp) } })
        pending?.let { put("pending", encodeTheme(it)) }
        put("lastExitMillis", lastExitMillis)
    }.toString()

    companion object {
        /** A ledger that cannot be read is an empty one. */
        fun decode(text: String): ThemeLedger? = try {
            val root = Json.parseToJsonElement(text).jsonObject
            ThemeLedger(
                themes = (root["themes"] as? JsonArray)?.mapNotNull { decodeTheme(it as? JsonObject) }.orEmpty(),
                refused = (root["refused"] as? JsonObject)?.mapValues { (_, releases) ->
                    (releases as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.let(ThemeRelease::parse) }?.toSet().orEmpty()
                }.orEmpty(),
                loading = (root["loading"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }?.toSet().orEmpty(),
                running = (root["running"] as? JsonObject)?.mapNotNull { (file, stamp) ->
                    (stamp as? JsonPrimitive)?.contentOrNull?.let { file to it }
                }?.toMap().orEmpty(),
                pending = decodeTheme(root["pending"] as? JsonObject),
                lastExitMillis = (root["lastExitMillis"] as? JsonPrimitive)?.longOrNull ?: 0,
            )
        } catch (_: Exception) { null }

        private fun encodeTheme(theme: InstalledTheme) = buildJsonObject {
            put("file", theme.file)
            put("stamp", theme.stamp)
            theme.id?.let { put("id", it) }
            theme.name?.let { put("name", it) }
            theme.release?.let { put("release", it.toString()) }
            theme.source?.let { put("source", it.wire) }
            theme.previous?.let { put("previous", it.toString()) }
            theme.trial?.let { trial -> put("trial", buildJsonObject { put("strikes", trial.strikes); put("shownMillis", trial.shownMillis) }) }
        }

        private fun decodeTheme(root: JsonObject?): InstalledTheme? {
            if (root == null) return null
            fun text(key: String) = (root[key] as? JsonPrimitive)?.contentOrNull
            val file = text("file") ?: return null
            val stamp = text("stamp") ?: return null
            val trial = root["trial"] as? JsonObject
            return InstalledTheme(
                file = file,
                stamp = stamp,
                id = text("id"),
                name = text("name"),
                release = text("release")?.let(ThemeRelease::parse),
                source = text("source")?.let(ThemeSource::fromWire),
                previous = text("previous")?.let(ThemeRelease::parse),
                trial = trial?.let {
                    ThemeTrial(
                        strikes = (it["strikes"] as? JsonPrimitive)?.intOrNull ?: 0,
                        shownMillis = (it["shownMillis"] as? JsonPrimitive)?.longOrNull ?: 0,
                    )
                },
            )
        }
    }
}

/** Why an archive cannot be installed over what is there. */
enum class ThemeInstallRefusal {
    /** Not a theme zip with a `theme.json` this host can read. */
    NotATheme,
    /** A version 1 manifest, which has no stable id. */
    NoId,
    /** The id of a pack Foldcade ships. */
    Bundled,
    /** No readable `release`, so no update could ever be compared with it. */
    NoRelease,
    /** It needs a newer Foldcade. */
    NewerHost,
    /** The installed pack with this id came from another source, or from none. */
    OtherSource,
    /** Not newer than the installed release. */
    NotNewer,
    /** This release went back once already. */
    WentBack,
    /** It did not load, or a shader did not compile. */
    Broken,
    /** The download is larger than any pack this host installs. */
    TooLarge,
}

/**
 * Whether [candidate] may be installed. A new id is a first install, which locks its source. The
 * same id must come from that same source with a higher release, never one that went back.
 */
fun checkThemeInstall(candidate: ThemeFile, ledger: ThemeLedger, bundled: Set<String>): ThemeInstallRefusal? {
    if (candidate.version < 2) return ThemeInstallRefusal.NoId
    if (candidate.id in bundled) return ThemeInstallRefusal.Bundled
    val release = candidate.release ?: return ThemeInstallRefusal.NoRelease
    val host = candidate.host
    if (host == null || host > THEME_HOST_LEVEL) return ThemeInstallRefusal.NewerHost
    if (release in ledger.refused[candidate.id].orEmpty()) return ThemeInstallRefusal.WentBack
    val installed = ledger.themes.firstOrNull { it.id == candidate.id } ?: return null
    if (installed.source != candidate.source) return ThemeInstallRefusal.OtherSource
    val current = installed.release
    if (current != null && release <= current) return ThemeInstallRefusal.NotNewer
    return null
}
