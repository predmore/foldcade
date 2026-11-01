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

    @Test fun theIndexEstablishesDocumentMembershipAndCurrentTreesEstablishTheGrant() {
        val elsewhere = image.replace("com.android.externalstorage.documents", "other")
        assertFalse(allowsLibraryArtworkUri(elsewhere, listOf(tree), setOf(elsewhere)))
        // Path validation belongs to enumeration and gamelist resolution, before index publication.
        for (uri in listOf("$image?token=x", "$tree/children", "$tree/document/%", "not a URI")) {
            assertFalse(uri, allowsLibraryArtworkUri(uri, listOf(tree), setOf(image)))
        }
    }

    @Test fun purposeSuffixRequiresMediaAndNoGamesInTheSameTree() {
        assertTrue(isMediaOnlyLibraryTree(tree, listOf(image), emptyList()))
        assertFalse(isMediaOnlyLibraryTree(tree, emptyList(), emptyList()))
        assertFalse(isMediaOnlyLibraryTree(tree, listOf(image), listOf(document("rom.gba"))))
        assertTrue(isMediaOnlyLibraryTree(tree, listOf(image), listOf("content://other/tree/root/document/rom.gba")))
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
