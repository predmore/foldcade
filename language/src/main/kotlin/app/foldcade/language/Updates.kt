package app.foldcade.language

/** Copy shared by Settings, R1 and the notes page. */
object UpdateCopy {
    const val title = "What’s new"
    const val automatic = "Update automatically"
    const val automaticCaption = "Foldcade and its plugins install updates when you’re not playing."
    const val prereleases = "Include pre-releases"
    const val prereleasesCaption = "Get Foldcade builds before they’re released."
    const val loading = "Loading…"
    const val failed = "Couldn’t load the notes. Check your connection and try again."
    const val empty = "No notes for this version."
    const val retry = "Try again"
    const val updatedPrefix = "foldcade:updated:"
    const val readyPrefix = "foldcade:update-ready:"
    fun updated(label: String) = "$label updated"
    fun body(version: String) = "$version · See what’s new"
    fun ready(label: String, version: String) = "$label $version is ready"
    const val readyBody = "Android asks before installing. Press A to install."
    fun from(version: String) = "Updated from $version"
}

data class ComponentVersion(val code: Long, val name: String, val previousCode: Long? = null, val previousName: String? = null)
data class ComponentUpdate(
    val packageName: String, val label: String, val fromCode: Long, val fromName: String?,
    val toCode: Long, val toName: String, val atMillis: Long,
)
data class ReleaseNotes(val code: Long?, val name: String, val body: String, val prerelease: Boolean = false, val tag: String = "")
data class UpdateLedger(val versions: Map<String, ComponentVersion> = emptyMap(), val unread: List<ComponentUpdate> = emptyList())

/** First sight is a baseline. Consecutive unread upgrades keep where the player started. */
fun UpdateLedger.observed(packageName: String, label: String, code: Long, name: String, now: Long): UpdateLedger {
    val old = versions[packageName]
    if (old?.code == code && old.name == name) return this
    val upgrade = old != null && code > old.code
    val version = ComponentVersion(code, name, if (upgrade) old?.code else old?.previousCode, if (upgrade) old?.name else old?.previousName)
    val pending = unread.firstOrNull { it.packageName == packageName }
    val updates = if (upgrade) unread.filterNot { it.packageName == packageName } + ComponentUpdate(
        packageName, label, pending?.fromCode ?: old!!.code, pending?.fromName ?: old?.name, code, name, now,
    ) else unread
    return copy(versions = versions + (packageName to version), unread = updates)
}

fun UpdateLedger.read(packageName: String): UpdateLedger = copy(unread = unread.filterNot { it.packageName == packageName })
fun ComponentUpdate.notice(): FoldNotice = FoldNotice(
    UpdateCopy.updatedPrefix + packageName, UpdateCopy.updated(label), UpdateCopy.body(toName), packageName, atMillis,
    updatePackage = packageName,
)
fun readyUpdateNotice(packageName: String, label: String, version: String, atMillis: Long): FoldNotice = FoldNotice(
    UpdateCopy.readyPrefix + packageName, UpdateCopy.ready(label, version), UpdateCopy.readyBody, packageName, atMillis,
    installUpdatePackage = packageName,
)

/** A process-killing self update waits longer than a plugin, even with both screens free. */
data class UpdateGate(
    val automatic: Boolean, val screensFree: Boolean, val saveSync: Boolean = false, val romDownload: Boolean = false,
    val themeInstall: Boolean = false, val backup: Boolean = false, val launching: Boolean = false,
    val screenOff: Boolean = false, val homeShown: Boolean = true, val idleMillis: Long = 0,
)
const val SELF_UPDATE_IDLE_MS = 10 * 60_000L
fun mayInstallUpdate(self: Boolean, gate: UpdateGate): Boolean = gate.automatic && gate.screensFree &&
    !gate.saveSync && !gate.romDownload && !gate.themeInstall && !gate.backup && !gate.launching &&
    (gate.screenOff || (gate.homeShown && (!self || gate.idleMillis >= SELF_UPDATE_IDLE_MS)))

/** Eligibility is deliberately narrower than having the release application id. */
fun mayUpdateFoldcade(packageName: String, origin: String, signers: Set<String>): Boolean =
    packageName == PluginRegistry.foldcade.packageName && origin == "ci" && signers == PluginRegistry.foldcade.signers

/** Release metadata is text, not APK trust. Keep pre-release notes only for the version actually installed. */
fun notesInRange(notes: List<ReleaseNotes>, from: Long, to: Long): List<ReleaseNotes> = notes
    .filter { it.code?.let { code -> code > from && code <= to && (!it.prerelease || (it.tag == "pre-release" && code == to)) } == true }
    .sortedBy { it.prerelease }.distinctBy { it.code }.sortedByDescending { it.code }

private val NOTE_HEADER = Regex("^(versionName|versionCode|commit):.*$")
private val MARKDOWN_LINK = Regex("\\[([^]]+)]\\([^)]*\\)")
private val GITHUB_CREDIT = Regex(" by @[A-Za-z0-9_-]+ in https://github\\.com/\\S+/pull/[0-9]+\\s*$")

/** Remove build bookkeeping and generated attribution, leaving readable release prose. */
fun cleanReleaseNotes(body: String): String = body.lineSequence().map(String::trimEnd).filterNot { raw ->
    val line = raw.trim()
    NOTE_HEADER.matches(line) || line.startsWith("Asset name:") || line.startsWith("The release asset is ") ||
        line.startsWith("Complete source") || line.startsWith("**Full Changelog**:")
}.map { raw ->
    val line = MARKDOWN_LINK.replace(GITHUB_CREDIT.replace(raw, ""), "$1")
    if (line.startsWith("* ") || line.startsWith("- ")) "• " + line.drop(2) else line
}.joinToString("\n").trim()

fun updateNotesText(update: ComponentUpdate, notes: List<ReleaseNotes>): String = buildString {
    appendLine("# ${update.label} ${update.toName}")
    update.fromName?.let { appendLine(); appendLine(UpdateCopy.from(it)) }
    if (notes.isEmpty()) { appendLine(); appendLine(UpdateCopy.empty) }
    notes.forEach { release ->
        appendLine()
        if (notes.size > 1) appendLine("## ${release.name}")
        appendLine(cleanReleaseNotes(release.body).ifBlank { UpdateCopy.empty })
    }
}.trim()

fun updatesHints(faces: FaceMap, retry: Boolean): List<HintKey> = buildList {
    if (retry) add(HintKey(promptKeyOf(faces.confirmKey) ?: PromptKey.FaceA, UpdateCopy.retry))
    addAll(licensesHints(faces))
}
