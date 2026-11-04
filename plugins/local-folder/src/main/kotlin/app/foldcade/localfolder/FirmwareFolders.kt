package app.foldcade.localfolder

/**
 * BIOS roots inside or beside a library, including ES-DE/Batocera layouts.
 * Paths are relative to a volume. The host still enforces the granted tree;
 * asking for a parent here never grants access to it.
 */
fun localBiosFolderPaths(libraryPath: String): List<String> {
    val path = libraryPath.trim('/')
    val parents = generateSequence(path) { current ->
        current.substringBeforeLast('/', "").takeUnless { it == current }
    }.take(4).toList()
    return buildList {
        if (path.substringAfterLast('/').equals("bios", true)) add(path)
        parents.forEach { parent ->
            listOf("bios", "BIOS").forEach { leaf -> add(listOf(parent, leaf).filter { it.isNotEmpty() }.joinToString("/")) }
        }
    }.distinct()
}
