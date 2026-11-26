package app.foldcade.plugins.ppsspp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PpssppMemstickTest {
    @Test fun sampleArgsNameTheBoundPspDirectory() {
        assertEquals("--memstick=\"/sdcard/PSP\"", candidateArgs("primary:PSP"))
        assertEquals("--memstick=\"/sdcard/saves/PSP\"", candidateArgs("primary:saves/PSP"))
    }

    @Test fun sampleArgsQuoteTheWholePathAndEscapeAnEmbeddedQuote() {
        assertEquals(
            "--memstick=\"/sdcard/QA With Spaces/PSP\"",
            candidateArgs("primary:QA With Spaces/PSP"),
        )
        assertEquals(
            "--memstick=\"/sdcard/QA \\\"one\\\"/PSP\"",
            candidateArgs("primary:QA \"one\"/PSP"),
        )
    }

    @Test fun sampleArgsRefuseNonPrimaryOrAmbiguousPaths() {
        for (documentId in listOf(
            "primary:", "primary:QA", "primary:PSP/SAVEDATA", "primary:/PSP", "primary:QA//PSP",
            "primary:QA/../PSP", "primary:QA/./PSP", "primary:QA\\dir/PSP", "primary:QA\u0000/PSP",
            "primary:Android/data/org.ppsspp.ppsspp/files/PSP", "primary:Android/obb/PSP",
            "1234-5678:PSP", "content://provider/tree/PSP",
        )) {
            assertNull(documentId, candidateArgs(documentId))
        }
    }

    @Test fun sampleArgsRequireProtectionBothAppsAccessAndSafeStartup() {
        assertNull(candidateArgs("primary:PSP", protected = false))
        assertNull(candidateArgs("primary:PSP", foldcadeHasWritableGrant = false))
        assertNull(candidateArgs("primary:PSP", ppssppCanWriteNativePath = false))
        assertNull(candidateArgs("primary:PSP", preservesSettings = false))
        assertNull(candidateArgs("primary:PSP", isColdStart = false))
    }

    // Candidate wire format for an isolated QA fixture. Production has no approved
    // version or way to establish these conditions and never sends this extra.
    private fun candidateArgs(
        documentId: String,
        protected: Boolean = true,
        foldcadeHasWritableGrant: Boolean = true,
        ppssppCanWriteNativePath: Boolean = true,
        preservesSettings: Boolean = true,
        isColdStart: Boolean = true,
    ): String? {
        if (!protected || !foldcadeHasWritableGrant || !ppssppCanWriteNativePath || !preservesSettings || !isColdStart) return null
        if (!documentId.startsWith("primary:")) return null
        val relative = documentId.removePrefix("primary:")
        val parts = relative.split('/')
        if (!parts.last().equals("PSP", ignoreCase = true)) return null
        if (parts.any { it.isEmpty() || it == "." || it == ".." || it.any { char -> char == '\\' || char.isISOControl() } }) return null
        if (relative.startsWith("Android/data/", ignoreCase = true) || relative.startsWith("Android/obb/", ignoreCase = true)) return null
        return "--memstick=\"/sdcard/${relative.replace("\"", "\\\"")}\""
    }
}
