package app.foldcade.baselineprofile

import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.UiDevice

internal const val PACKAGE = "app.foldcade.benchmark"

/** Thor-only, deliberately fail closed before a key could reach the real Home. */
internal class HomeJourney(private val device: UiDevice) {
    fun awaitPanels() {
        val deadline = SystemClock.uptimeMillis() + 15_000
        do {
            val activities = device.executeShellCommand("dumpsys activity activities")
            var display = -1
            val resumed = mutableMapOf<Int, String>()
            activities.lineSequence().forEach { line ->
                if (line.startsWith("Display #")) display = line.substringAfter('#').substringBefore(' ').toInt()
                if ("topResumedActivity=" in line) resumed[display] = line
            }
            if (resumed[0]?.contains("$PACKAGE/app.foldcade.PrimaryHomeActivity") == true &&
                resumed[4]?.contains("$PACKAGE/app.foldcade.CompanionHomeActivity") == true) return
            SystemClock.sleep(150)
        } while (SystemClock.uptimeMillis() < deadline)
        error("Both Thor displays must have $PACKAGE resumed; refusing input")
    }

    fun awaitDrawn() {
        val deadline = SystemClock.uptimeMillis() + 10_000
        do {
            val windows = device.executeShellCommand("dumpsys window windows")
                .split(Regex("(?m)^  Window #"))
            val drawn = listOf(0 to "PrimaryHomeActivity", 4 to "CompanionHomeActivity").all { (display, activity) ->
                windows.any { block ->
                    block.lineSequence().first().contains("$PACKAGE/app.foldcade.$activity}") &&
                        "mDisplayId=$display " in block && "mHasSurface=true" in block && "mDrawState=HAS_DRAWN" in block
                }
            }
            if (drawn) return
            SystemClock.sleep(100)
        } while (SystemClock.uptimeMillis() < deadline)
        error("Both Thor Home windows must have drawn")
    }

    fun key(name: String, display: Int = 4, settle: Long = 250) {
        awaitPanels()
        device.executeShellCommand("input -d $display keyevent $name")
        SystemClock.sleep(settle)
    }

    fun grid() {
        repeat(4) { key("DPAD_RIGHT", settle = 80) }
        repeat(4) { key("DPAD_LEFT", settle = 80) }
        awaitPanels()
        // Android's own repeat, from the built-in Thor pad. A keyevent longpress is too short.
        check("Xbox Wireless Controller" in device.executeShellCommand("getevent -pl /dev/input/event9"))
        try {
            device.executeShellCommand("sendevent /dev/input/event9 1 547 1")
            device.executeShellCommand("sendevent /dev/input/event9 0 0 0")
            SystemClock.sleep(800)
        } finally {
            device.executeShellCommand("sendevent /dev/input/event9 1 547 0")
            device.executeShellCommand("sendevent /dev/input/event9 0 0 0")
        }
        SystemClock.sleep(400)
        repeat(12) { key("DPAD_LEFT", settle = 50) }
    }

    fun firstMinute() {
        grid()
        Log.i("FoldcadeProfile", "Settings rows")
        key("BUTTON_START")
        key("DPAD_RIGHT") // enter the rows without changing a setting
        repeat(3) { key("DPAD_DOWN") }
        repeat(3) { key("DPAD_UP") }
        key("BUTTON_START")

        Log.i("FoldcadeProfile", "L1 Games / All games")
        key("BUTTON_L1", 0)
        key("DPAD_DOWN", 0) // Play activity is the initial row; down enters Home/Games/Apps
        key("DPAD_RIGHT", 0)
        key("BUTTON_A", 0, settle = 600) // All games

        Log.i("FoldcadeProfile", "L1 Apps")
        key("BUTTON_L1", 0)
        key("DPAD_DOWN", 0)
        repeat(2) { key("DPAD_RIGHT", 0) }
        key("BUTTON_A", 0, settle = 600)

        // A real installed app offers tile actions even on a fresh install with no ROMs.
        Log.i("FoldcadeProfile", "Y actions")
        key("BUTTON_Y", settle = 600)
        key("BUTTON_R1", 0, settle = 600) // retire the left actions and open the right island
        key("BUTTON_R1", 0) // close it

        Log.i("FoldcadeProfile", "L1 Home")
        key("BUTTON_L1", 0)
        key("DPAD_DOWN", 0)
        key("BUTTON_A", 0)

        Log.i("FoldcadeProfile", "R1 command center")
        key("BUTTON_R1", 0)
        repeat(3) { key("DPAD_RIGHT", 0) }
        key("BUTTON_R1", 0)
        Log.i("FoldcadeProfile", "Find")
        key("BUTTON_SELECT")
        key("DPAD_RIGHT")
        key("BUTTON_SELECT")
    }
}

internal fun MacrobenchmarkScope.startHome() {
    startActivityAndWait(Intent(Intent.ACTION_MAIN).apply {
        setClassName(PACKAGE, "app.foldcade.PrimaryHomeActivity")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
    HomeJourney(device).apply { awaitPanels(); awaitDrawn() }
    SystemClock.sleep(600)
}
