package app.foldcade.language

import java.util.Locale

/**
 * Which Moonlight list the shelf shows.
 * Switching this never changes [MoonlightPlacement]s.
 */
enum class MoonlightSource {
    ImportedList,
    PinnedShortcuts,
}

fun cycleMoonlightSource(source: MoonlightSource): MoonlightSource = when (source) {
    MoonlightSource.ImportedList -> MoonlightSource.PinnedShortcuts
    MoonlightSource.PinnedShortcuts -> MoonlightSource.ImportedList
}

fun moonlightSourceLabel(source: MoonlightSource): String = when (source) {
    MoonlightSource.ImportedList -> Copy.moonlightImported
    MoonlightSource.PinnedShortcuts -> Copy.moonlightPinned
}

/**
 * The Moonlight folder id the home grid already uses.
 * Import records membership here. It does not draw that grid.
 */
const val MOONLIGHT_FOLDER_ID: String = "home.moonlight"

/**
 * One imported game's place in All Apps and the Moonlight folder.
 * A source change does not remove these.
 */
data class MoonlightPlacement(
    val remoteKey: String,
    val allApps: Boolean,
    val moonlightFolder: Boolean,
)

/** A host and app the shortcut query already found. Not a row from Moonlight's database. */
data class MoonlightDiscoveredApp(
    val hostUuid: String,
    val hostName: String,
    val appId: String,
    val label: String,
)

/** One row on the confirm sheet. Checked rows are the ones Import keeps. */
data class MoonlightSheetApp(
    val hostUuid: String,
    val hostName: String,
    val appId: String,
    val label: String,
    val checked: Boolean,
) {
    /** Same key as `MoonlightApp.remoteKey`: lowercase host UUID, a bar, then the app id. */
    val remoteKey: String = "${hostUuid.lowercase(Locale.ROOT)}|$appId"
}

/** Where focus sits. The first app opens focused, so the filled pill is on a row. */
enum class MoonlightSheetTarget {
    App,
    Import,
    NotNow,
}

data class MoonlightImportSheet(
    val apps: List<MoonlightSheetApp>,
    val index: Int,
    val screen: HostScreen,
) {
    val target: MoonlightSheetTarget
        get() = when {
            index < apps.size -> MoonlightSheetTarget.App
            index == apps.size -> MoonlightSheetTarget.Import
            else -> MoonlightSheetTarget.NotNow
        }

    fun focusedApp(): MoonlightSheetApp? = apps.getOrNull(index)
}

data class MoonlightSheetSection(
    val hostUuid: String,
    val hostName: String,
    val rows: List<MoonlightSheetRow>,
)

data class MoonlightSheetRow(
    val index: Int,
    val app: MoonlightSheetApp,
)

sealed interface MoonlightSheetResult {
    data class Import(val checked: List<MoonlightSheetApp>) : MoonlightSheetResult
    data object NotNow : MoonlightSheetResult
}

/** Stored import row. The shelf rebuilds a Moonlight app from these three fields. */
data class MoonlightStoredApp(
    val hostUuid: String,
    val appId: String,
    val label: String,
)

/**
 * One sheet for every discovered host. Every app starts checked.
 * Focus opens on the first app. An empty discovery does not open a sheet.
 */
fun moonlightImportSheet(
    discovered: List<MoonlightDiscoveredApp>,
    screen: HostScreen,
): MoonlightImportSheet? {
    val apps = discovered.mapNotNull { app ->
        val uuid = app.hostUuid.trim()
        val id = app.appId.trim()
        val label = app.label.trim()
        if (uuid.isEmpty() || id.isEmpty() || label.isEmpty()) return@mapNotNull null
        MoonlightSheetApp(
            hostUuid = uuid,
            hostName = app.hostName.trim().ifEmpty { uuid },
            appId = id,
            label = label,
            checked = true,
        )
    }.distinctBy { it.remoteKey }
    if (apps.isEmpty()) return null
    return MoonlightImportSheet(apps = apps, index = 0, screen = screen)
}

/** Host sections in first-seen order. Later apps from the same host stay in that section. */
fun moonlightSheetSections(apps: List<MoonlightSheetApp>): List<MoonlightSheetSection> {
    val order = apps.map { it.hostUuid }.distinct()
    return order.map { uuid ->
        val rows = apps.mapIndexedNotNull { index, app ->
            if (app.hostUuid == uuid) MoonlightSheetRow(index, app) else null
        }
        MoonlightSheetSection(
            hostUuid = uuid,
            hostName = rows.first().app.hostName,
            rows = rows,
        )
    }
}

/**
 * Focus stays inside the sheet. Up and down walk the apps, then the button row.
 * Left and right move between Import and Not now. Activate toggles a check.
 * Back is Not now.
 */
fun applyMoonlightSheet(
    sheet: MoonlightImportSheet,
    meaning: Meaning,
): Pair<MoonlightImportSheet, MoonlightSheetResult?> {
    val lastApp = sheet.apps.lastIndex
    val importIndex = sheet.apps.size
    val notNowIndex = sheet.apps.size + 1
    val index = sheet.index.coerceIn(0, notNowIndex)
    return when (meaning) {
        Meaning.MoveUp -> {
            val next = if (index >= importIndex) lastApp.coerceAtLeast(0) else (index - 1).coerceAtLeast(0)
            sheet.copy(index = next) to null
        }
        Meaning.MoveDown -> {
            val next = when {
                index >= importIndex -> index
                index == lastApp -> importIndex
                else -> index + 1
            }
            sheet.copy(index = next) to null
        }
        Meaning.MoveLeft -> {
            val next = if (index == notNowIndex) importIndex else index
            sheet.copy(index = next) to null
        }
        Meaning.MoveRight -> {
            val next = if (index == importIndex) notNowIndex else index
            sheet.copy(index = next) to null
        }
        Meaning.Activate -> when {
            index < sheet.apps.size -> {
                val apps = sheet.apps.mapIndexed { at, app ->
                    if (at == index) app.copy(checked = !app.checked) else app
                }
                sheet.copy(apps = apps, index = index) to null
            }
            index == importIndex -> sheet to MoonlightSheetResult.Import(sheet.apps.filter { it.checked })
            else -> sheet to MoonlightSheetResult.NotNow
        }
        Meaning.Back -> sheet to MoonlightSheetResult.NotNow
        else -> sheet.copy(index = index) to null
    }
}

/**
 * Adds each checked app to All Apps and the Moonlight folder.
 * Apps already placed stay, including ones this import left unchecked.
 */
fun placeMoonlightImport(
    existing: List<MoonlightPlacement>,
    checked: List<MoonlightSheetApp>,
): List<MoonlightPlacement> {
    val known = existing.associateBy { it.remoteKey }
    val extra = checked
        .map { it.remoteKey }
        .distinct()
        .filter { it !in known }
        .map { key ->
            MoonlightPlacement(
                remoteKey = key,
                allApps = true,
                moonlightFolder = true,
            )
        }
    return (existing + extra).sortedBy { it.remoteKey }
}

fun moonlightPlacementKeys(placements: List<MoonlightPlacement>): Set<String> =
    placements.map { it.remoteKey }.toSet()

fun moonlightPlacementsFromKeys(keys: Set<String>): List<MoonlightPlacement> =
    keys.map { key ->
        MoonlightPlacement(remoteKey = key, allApps = true, moonlightFolder = true)
    }.sortedBy { it.remoteKey }

fun encodeMoonlightApps(apps: List<MoonlightStoredApp>): String =
    apps.joinToString(RECORD) { app ->
        listOf(app.hostUuid, app.appId, app.label).joinToString(FIELD) { field ->
            field.replace(FIELD, " ").replace(RECORD, " ")
        }
    }

fun decodeMoonlightApps(raw: String?): List<MoonlightStoredApp> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(RECORD).mapNotNull { record ->
        val bits = record.split(FIELD)
        if (bits.size != 3) return@mapNotNull null
        val uuid = bits[0]
        val id = bits[1]
        val label = bits[2]
        if (uuid.isBlank() || id.isBlank() || label.isBlank()) return@mapNotNull null
        MoonlightStoredApp(hostUuid = uuid, appId = id, label = label)
    }
}

private const val FIELD: String = "\u001f"
private const val RECORD: String = "\u001e"
