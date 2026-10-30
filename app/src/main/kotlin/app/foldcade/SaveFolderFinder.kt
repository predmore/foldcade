package app.foldcade

import app.foldcade.api.plugin.SaveFolderHolder

internal data class SaveFolderGrant(val uri: String, val read: Boolean, val write: Boolean)
/** [placeStart] is a place that is there; [startingFolder] falls back to hints that may not be. */
internal data class SaveFolderFind(val folder: String?, val startingFolder: String?, val candidates: List<String>, val placeStart: String? = null)
internal data class SavedSaveFolder(val uri: String, val found: Boolean)

/** A chosen folder stays chosen. Only a found folder can be cleared or replaced by detection. */
internal fun detectedSaveFolder(saved: SavedSaveFolder?, reachable: (String) -> Boolean, find: () -> String?): SavedSaveFolder? {
    if (saved != null && (!saved.found || reachable(saved.uri))) return saved
    return find()?.let { SavedSaveFolder(it, found = true) }
}

/** Finds one reachable save folder without choosing between plausible copies. */
internal class SaveFolderFinder(
    private val grants: List<SaveFolderGrant>,
    private val isDirectory: (String) -> Boolean,
    private val childNames: (String) -> List<String>?,
    private val playerGrant: (String) -> Boolean,
    private val placeExists: (String) -> Boolean,
) {
    fun find(holder: SaveFolderHolder): SaveFolderFind {
        val places = holder.saveFolderPlaces.distinct().map { path ->
            val id = "primary:$path"
            val uri = reachable(id)
            val exists = uri?.let(isDirectory) == true
            val inPlayer = playerGrant(id)
            Place(path, uri, exists || placeExists(path), exists && fits(holder, uri!!) && (!holder.saveFolderPickedInPlayer || inPlayer), inPlayer)
        }
        val counting = places.filter { it.counts }
        // A player given two of its places keeps one of them, and its grants cannot say which.
        val playerHasOne = !holder.saveFolderPickedInPlayer || places.count { it.inPlayer } <= 1
        val chosen = (counting.singleOrNull() ?: counting.filter { it.inPlayer }.singleOrNull())?.takeIf { playerHasOne }
        val start = (counting.firstOrNull() ?: places.firstOrNull { it.exists })?.let { primaryFolderDocument(it.path) }
        return SaveFolderFind(
            chosen?.uri,
            start ?: holder.suggestedSaveFolder ?: places.firstOrNull()?.let { primaryFolderDocument(it.path) },
            counting.mapNotNull { it.uri },
            start,
        )
    }

    fun reachable(documentId: String): String? = grants.asSequence()
        .filter { it.read && it.write && containsFolder(folderTreeId(it.uri), documentId) }
        .maxByOrNull { folderTreeId(it.uri).length }
        ?.let { if (folderTreeId(it.uri) == documentId) folderGrantUri(it.uri) else folderDocumentUri(it.uri, documentId) }

    fun fits(holder: SaveFolderHolder, uri: String): Boolean = holder.saveFolderMarkers.isEmpty() ||
        childNames(uri)?.any { name -> holder.saveFolderMarkers.any { it.equals(name, ignoreCase = true) } } == true

    /** A broad pick can contain a known place or one unambiguous child with the player's markers. */
    fun narrow(holder: SaveFolderHolder, picked: String): String? {
        if (holder.saveFolderMarkers.isNotEmpty() && fits(holder, picked)) return picked
        val inside = SaveFolderFinder(listOf(SaveFolderGrant(picked, true, true)), isDirectory, childNames, playerGrant, placeExists)
        val contained = object : SaveFolderHolder by holder {
            override val saveFolderPlaces = holder.saveFolderPlaces.filter { containsFolder(folderDocumentId(picked), "primary:$it") }
        }
        val result = inside.find(contained)
        if (result.folder != null) return result.folder
        if (result.candidates.isNotEmpty()) return null
        if (holder.saveFolderMarkers.isNotEmpty()) {
            val id = folderDocumentId(picked)
            return childNames(picked).orEmpty().map { folderDocumentUri(picked, if (id.endsWith(':')) "$id$it" else "$id/$it") }
                .filter { isDirectory(it) && fits(holder, it) && (!holder.saveFolderPickedInPlayer || playerGrant(folderDocumentId(it))) }
                .singleOrNull()
        }
        return picked
    }

    private data class Place(val path: String, val uri: String?, val exists: Boolean, val counts: Boolean, val inPlayer: Boolean)
}
