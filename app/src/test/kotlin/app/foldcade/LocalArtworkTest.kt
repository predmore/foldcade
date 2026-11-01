package app.foldcade

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class LocalArtworkTest {
    private val tree = "content://com.android.externalstorage.documents/tree/primary%3Aroms"
    private fun document(id: String) = "$tree/document/$id"
    private val image = document("primary%3Aroms%2Fgba%2Fmedia%2FGame.png")

    @Test fun onlyEnumeratedImagesUnderCurrentLibraryTreesAreAllowed() {
        assertTrue(allowsLibraryArtworkUri(image, listOf(tree), setOf(image)))
        assertFalse(allowsLibraryArtworkUri(image, emptyList(), setOf(image)))
        assertFalse(allowsLibraryArtworkUri(image, listOf(tree), emptySet()))
        assertFalse(allowsLibraryArtworkUri(image, listOf(tree.replace("roms", "roms2")), setOf(image)))
        val plus = document("primary%3Aroms%2FA+B.png")
        assertTrue(allowsLibraryArtworkUri(plus, listOf(tree), setOf(plus)))
    }

    @Test fun rejectsBorrowedGrantsTraversalOtherAuthoritiesQueriesAndMalformedUris() {
        for (uri in listOf(document("primary%3Aroms2%2Fsecret.png"), document("primary%3Aroms%2F..%2Fsecret.png"),
            document("primary%3Aother%2Fsecret.png"), image.replace("com.android.externalstorage.documents", "other"),
            "$image?token=x", "$image#fragment", image.replace("content:", "file:"), "$tree/children",
            "content://bad%/tree/x/document/y", "$tree/document/%", "content://user@provider/tree/x/document/y")) {
            assertFalse(uri, allowsLibraryArtworkUri(uri, listOf(tree), setOf(uri)))
        }
    }

    @Test fun opaqueProviderIdsNeedBothEnumerationAndTheMatchingTreeGrant() {
        val tree = "content://provider/tree/root-uuid"
        val image = "$tree/document/opaque-image-uuid"
        assertTrue(allowsLibraryArtworkUri(image, listOf(tree), setOf(image)))
        assertFalse(allowsLibraryArtworkUri(image, listOf("content://provider/tree/other"), setOf(image)))
    }

    @Test fun byteLimitAcceptsAnExactBodyAndRefusesOversizedBodies() {
        assertArrayEquals(byteArrayOf(1, 2), readBoundedBytes(ByteArrayInputStream(byteArrayOf(1, 2)), 2))
        assertNull(readBoundedBytes(ByteArrayInputStream(byteArrayOf(1, 2, 3)), 2))
        assertArrayEquals(byteArrayOf(), readBoundedBytes(ByteArrayInputStream(byteArrayOf()), 2))
    }
}
