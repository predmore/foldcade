package app.foldcade

import app.foldcade.language.provingSignal
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.*
import org.junit.Test

class AzaharLogVersionTest {
    @Test fun realThorLogMatchesTheResolvedFlavorsPackageVersion() {
        val relative = "plugins/azahar/src/test/resources/azahar_log.txt"
        val fixture = listOf(Paths.get(relative), Paths.get("..").resolve(relative)).first(Files::isRegularFile)
        val version = azaharLogVersion(Files.readString(fixture))
        assertEquals("2126.2", version)
        assertTrue(provingSignal(true, false, 2, version, "2126.2"))
        assertFalse(provingSignal(true, false, 2, version, "2126.1"))
        assertNull(azaharLogVersion("Azahar starting..."))
    }
}
