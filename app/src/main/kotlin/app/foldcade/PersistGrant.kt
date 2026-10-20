package app.foldcade

import android.content.Intent

/**
 * Keeps a document-tree grant. A save folder asks for write, then read.
 * A library asks for read only. Either call can throw when the grant is
 * not persistable; a thrown read after a failed write used to crash the
 * picker result.
 *
 * Returns false when nothing could be kept. The caller leaves the previous
 * folder in place.
 */
internal fun persistTreeGrant(write: Boolean, take: (Int) -> Unit): Boolean {
    val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
    if (write) {
        try {
            take(read or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            return true
        } catch (_: SecurityException) {
            // The provider may allow a read grant when write is refused.
        }
    }
    return try {
        take(read)
        true
    } catch (_: SecurityException) {
        false
    }
}
