package app.foldcade.baselineprofile

import android.annotation.SuppressLint
import androidx.benchmark.Shell
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Before

/**
 * Firmware 377 installs successfully but emits this libselinux warning on stderr. Benchmark
 * 1.5 treats any stderr as failure. Opt in only on this shared Thor, under the device lock;
 * keep every other diagnostic and restore Benchmark's wrapper when the test finishes.
 */
@SuppressLint("RestrictedApi")
open class ThorShell {
    private var original: String? = null
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before fun filterThorInstallWarning() {
        if (InstrumentationRegistry.getArguments().getString("foldcade.thorShellWorkaround") != "true") return
        check(device.executeShellCommand("getprop ro.build.display.id").startsWith("Thor_V1.0.0.377_"))
        Shell.executeScriptCaptureStdoutStderr("true") // create the pinned Benchmark 1.5 wrapper
        original = device.executeShellCommand("cat /data/local/tmp/shellWrapper.sh")
        check(original!!.contains("/system/bin/sh $1 2> $2"))
        Shell.executeScriptCaptureStdoutStderr(
            "printf '\\n%s\\n' 'sed -i \"/^avc:  could not determine enforcing mode: Permission denied$/d\" \"\u00242\"' >> /data/local/tmp/shellWrapper.sh",
        )
        check(device.executeShellCommand("cat /data/local/tmp/shellWrapper.sh").contains("sed -i"))
    }

    @After fun restoreBenchmarkShell() {
        val saved = original ?: return
        val quoted = "'" + saved.replace("'", "'\\''") + "'"
        Shell.executeScriptCaptureStdoutStderr("printf '%s' $quoted > /data/local/tmp/shellWrapper.sh")
        original = null
    }
}
