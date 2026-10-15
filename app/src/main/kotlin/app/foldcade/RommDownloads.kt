package app.foldcade

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.stream.Collectors

/**
 * Moves RomM downloads an older version kept in the app cache to [to], the
 * shared media folder, so a player that opens games by path can read them.
 * Every file under [from] moves to the same place under [to]. A file already
 * at [to] wins; the old copy is a download and is dropped. Each file is copied
 * beside its new place and renamed into it, so an interrupted move leaves no
 * half file under its real name. Folders left empty are removed.
 *
 * A file [stays] picks out by its path under [from] is left where it is.
 *
 * Runs before RomM is wired, so nothing reads either root meanwhile.
 * Returns false when something could not be moved; the next start tries again.
 */
internal fun moveRommDownloads(from: Path, to: Path, stays: (relative: Path) -> Boolean = { false }): Boolean {
    if (!Files.isDirectory(from)) return true
    if (from.toAbsolutePath().normalize() == to.toAbsolutePath().normalize()) return true
    var moved = true
    val files = Files.walk(from).use { stream ->
        stream.filter { Files.isRegularFile(it) }.collect(Collectors.toList())
    }
    for (file in files) {
        val relative = from.relativize(file)
        if (stays(relative)) continue
        val dest = to.resolve(relative.toString())
        try {
            if (!Files.exists(dest)) {
                Files.createDirectories(dest.parent)
                val partial = dest.resolveSibling(dest.fileName.toString() + MOVING_SUFFIX)
                Files.copy(file, partial, StandardCopyOption.REPLACE_EXISTING)
                Files.move(partial, dest, StandardCopyOption.ATOMIC_MOVE)
            }
            Files.delete(file)
        } catch (_: IOException) {
            moved = false
        }
    }
    val folders = Files.walk(from).use { stream ->
        stream.filter { Files.isDirectory(it) }.collect(Collectors.toList())
    }
    // Deepest first, so a parent is empty by the time it is reached.
    for (folder in folders.sortedByDescending { it.nameCount }) {
        try {
            Files.deleteIfExists(folder)
        } catch (_: IOException) {
            Unit
        }
    }
    return moved
}

private const val MOVING_SUFFIX = ".moving"
