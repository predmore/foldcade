package app.foldcade

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager

/**
 * Keeps the CPU and Wi-Fi awake while a game downloads from RomM, so a screen
 * turned off by the power button or the lid does not stall it. Home's panels
 * keep the screen itself on meanwhile (see `PanelHost`), so the screen timeout
 * never puts the Thor to sleep mid-download. Main thread only.
 */
internal class DownloadAwake(context: Context) {
    private val cpu = context.getSystemService(PowerManager::class.java)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "foldcade:download")
        .apply { setReferenceCounted(false) }

    // High-perf is the lock that keeps Wi-Fi at full power with the screen off on Android 13.
    @Suppress("DEPRECATION")
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
        ?.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "foldcade:download")
        ?.apply { setReferenceCounted(false) }

    private val main = Handler(Looper.getMainLooper())

    // The limit only guards against a download that never says it ended. A Wi-Fi lock takes no timeout.
    private val expire = Runnable { if (wifi?.isHeld == true) wifi.release() }

    fun hold(downloading: Boolean) {
        main.removeCallbacks(expire)
        if (downloading) {
            cpu.acquire(LIMIT_MS)
            wifi?.acquire()
            main.postDelayed(expire, LIMIT_MS)
        } else {
            if (cpu.isHeld) cpu.release()
            if (wifi?.isHeld == true) wifi.release()
        }
    }

    private companion object {
        const val LIMIT_MS = 6 * 60 * 60_000L
    }
}
