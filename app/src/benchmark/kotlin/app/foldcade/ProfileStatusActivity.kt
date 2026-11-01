package app.foldcade

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.profileinstaller.ProfileVerifier
import java.io.File

/** Shell diagnostics for a plain APK install. This activity never ships in release. */
class ProfileStatusActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = ProfileVerifier.getCompilationStatusAsync()
        result.addListener({
            val status = result.get()
            val profile = File("/data/misc/profiles/cur/0/$packageName/primary.prof")
            Log.i("FoldcadeProfileStatus", "curBytes=${profile.length()} result=${status.profileInstallResultCode} " +
                "compiled=${status.isCompiledWithProfile} queued=${status.hasProfileEnqueuedForCompilation()}")
            finish()
        }, mainExecutor)
    }
}
