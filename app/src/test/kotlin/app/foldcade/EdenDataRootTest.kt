package app.foldcade

import app.foldcade.plugins.eden.EDEN_PACKAGES
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EdenDataRootTest {
    @Test fun onlyTheInstalledBuildsExactDataRootCounts() {
        for (pkg in EDEN_PACKAGES) {
            assertTrue(edenDataRoot(pkg, "$pkg.user", "root"))
            assertTrue(edenDataRoot(pkg, "$pkg.user", "root/"))
            assertTrue(edenDataRoot(pkg, "com.android.externalstorage.documents", "primary:Android/data/$pkg/files"))
            for (id in listOf("root/nand", "primary:Android/data/$pkg", "primary:Android/data/$pkg/files/nand", "primary:Download")) {
                assertFalse(edenDataRoot(pkg, "$pkg.user", id))
                assertFalse(edenDataRoot(pkg, "com.android.externalstorage.documents", id))
            }
            for (other in EDEN_PACKAGES - pkg) {
                assertFalse(edenDataRoot(pkg, "$other.user", "root"))
                assertFalse(edenDataRoot(pkg, "com.android.externalstorage.documents", "primary:Android/data/$other/files"))
            }
            assertFalse(edenDataRoot(pkg, "unrelated.provider", "root"))
        }
    }
}
