package app.foldcade

import app.foldcade.api.plugin.LaunchTarget
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path

/**
 * The absolute path another app can open for a game's content URI, for a
 * player that opens games by path ([app.foldcade.api.plugin.Player.opensByPath]).
 *
 * Two kinds of URI have one:
 * - a document of Android's external storage provider, which a local folder
 *   lists. Its document id is the volume and the path on it;
 * - a file Foldcade serves itself, through [ownFile], that lies in one of
 *   [sharedRoots]. RomM downloads live in Foldcade's shared media folder.
 *
 * Anything else, a cloud document or a file in private storage, has no such
 * path, and the answer is null. This type does not copy a file.
 */
internal class SharedPaths(
    private val ownFile: (String) -> Path?,
    private val sharedRoots: () -> List<Path>,
) {
    fun target(uri: String): LaunchTarget.SharedFile? = pathFor(uri)?.let { LaunchTarget.SharedFile(it, uri) }

    fun pathFor(uri: String): String? = externalStoragePath(uri) ?: ownSharedPath(uri)

    private fun ownSharedPath(uri: String): String? {
        val file = ownFile(uri)?.toAbsolutePath()?.normalize() ?: return null
        val inShared = sharedRoots().any { root -> file.startsWith(root.toAbsolutePath().normalize()) }
        if (!inShared || !Files.isRegularFile(file)) return null
        return file.toString()
    }

    companion object {
        const val EXTERNAL_STORAGE_AUTHORITY: String = "com.android.externalstorage.documents"
        private const val STORAGE = "/storage/"
        private const val PRIMARY = "/storage/emulated/0"

        /** A removable volume's id, as the provider names it: `1A2B-3C4D`. */
        private val VOLUME = Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")

        /**
         * `content://com.android.externalstorage.documents/document/<id>`, or the
         * same document inside a tree, `.../tree/<id>/document/<id>`. The id is
         * `primary:<path>`, `home:<path>` (the Documents folder), or `<volume>:<path>`.
         */
        fun externalStoragePath(uri: String): String? {
            val prefix = "content://$EXTERNAL_STORAGE_AUTHORITY/"
            if (!uri.startsWith(prefix)) return null
            val segments = uri.removePrefix(prefix).substringBefore('?').substringBefore('#').split('/')
            val documentId = when {
                segments.size == 2 && segments[0] == "document" -> segments[1]
                segments.size == 4 && segments[0] == "tree" && segments[2] == "document" -> segments[3]
                else -> return null
            }.let(::percentDecode) ?: return null
            val volume = documentId.substringBefore(':', missingDelimiterValue = "")
            val relative = documentId.substringAfter(':', missingDelimiterValue = "")
            val base = when {
                volume == "primary" -> PRIMARY
                volume == "home" -> "$PRIMARY/Documents"
                VOLUME.matches(volume) -> "$STORAGE$volume"
                else -> return null
            }
            val parts = relative.split('/').filter { it.isNotEmpty() }
            if (parts.isEmpty() || parts.any { it == "." || it == ".." || '\u0000' in it }) return null
            return base + "/" + parts.joinToString("/")
        }

        /** RFC 3986 percent-decoding as UTF-8. A `+` stays a plus. Null for a broken escape. */
        private fun percentDecode(text: String): String? {
            val out = ByteArrayOutputStream(text.length)
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '%') {
                    if (i + 2 >= text.length) return null
                    val byte = text.substring(i + 1, i + 3).toIntOrNull(16) ?: return null
                    out.write(byte)
                    i += 3
                } else {
                    out.write(c.toString().toByteArray(Charsets.UTF_8))
                    i += 1
                }
            }
            return out.toString(Charsets.UTF_8.name())
        }
    }
}
