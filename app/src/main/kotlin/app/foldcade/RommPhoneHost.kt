package app.foldcade

import android.net.ConnectivityManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.foldcade.language.KeyDrop
import app.foldcade.language.KeyDropPage
import app.foldcade.language.RommPhonePage
import app.foldcade.net.LanDrop
import app.foldcade.romm.normalizeRommPhoneAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** The address handoff lives only beside the visible RomM form. Main, except the listener and IO job. */
internal class RommPhoneHost(
    private val connectivity: ConnectivityManager?,
    private val scope: CoroutineScope,
    private val colors: () -> KeyDropPage.Colors,
    private val onAddress: suspend (String) -> String?,
    private val main: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val addressOf: () -> java.net.InetAddress? = { lanAddress(connectivity) },
) {
    var state by mutableStateOf<KeyDrop?>(null)
        private set
    private var epoch = 0
    private var wanted = false
    private var drop: LanDrop? = null
    private var opening: Job? = null

    fun want(on: Boolean, retry: Boolean) {
        if (wanted == on) {
            if (on && retry && state == KeyDrop.Sent) arm()
            return
        }
        wanted = on
        if (on) arm() else {
            epoch += 1
            opening?.cancel()
            drop.also { drop = null }?.close()
            state = null
        }
    }

    private fun arm() {
        val attempt = ++epoch
        opening?.cancel()
        drop.also { drop = null }?.close()
        state = null
        val pageColors = colors()
        opening = scope.launch(Dispatchers.IO) {
            while (true) {
                val address = addressOf()
                val opened = address?.let { address ->
                    runCatching {
                        // The listener never uses the server until Main has installed it below.
                        LanDrop.open(address, object : LanDrop.Pages {
                            override fun form(action: String) = RommPhonePage.form(action, pageColors)
                            override fun done(received: Boolean) = RommPhonePage.done(null, !received, pageColors)
                            override fun received(value: String?): String {
                                val origin = value?.let(::normalizeRommPhoneAddress)
                                // This is LanDrop's thread. Main keeps drawing while RomM creates a challenge.
                                val url = if (origin == null) null else runBlocking {
                                    withTimeoutOrNull(10_000) {
                                        withContext(main) {
                                            if (!wanted || epoch != attempt) return@withContext null
                                            state = KeyDrop.Sent
                                            onAddress(origin)
                                        }
                                    }
                                }
                                return RommPhonePage.done(url, origin == null, pageColors)
                            }
                        }, object : LanDrop.Listener {
                            override fun visited() { scope.launch(main) {
                                if (wanted && epoch == attempt) (state as? KeyDrop.Open)?.let { state = it.copy(phoneHere = true) }
                            } }
                            override fun received(value: String?) { scope.launch(main) {
                                if (wanted && epoch == attempt && (value?.let(::normalizeRommPhoneAddress) == null || state == KeyDrop.Sent)) arm()
                            } }
                            override fun stopped() { scope.launch(main) {
                                if (wanted && epoch == attempt && drop?.isOpen == false) arm()
                            } }
                        })
                    }.getOrNull()
                }
                if (opened == null) {
                    withContext(main) { if (wanted && epoch == attempt) state = KeyDrop.Offline }
                    delay(5_000)
                    continue
                }
                var held = false
                try {
                    withContext(main) {
                        if (wanted && epoch == attempt) {
                            drop = opened
                            state = KeyDrop.Open(opened.url)
                            held = true
                        }
                    }
                } finally { if (!held) opened.close() }
                while (opened.isOpen) {
                    delay(2_000)
                    if (opened.isOpen && addressOf() != address) {
                        withContext(main) { if (wanted && drop === opened) arm() }
                        return@launch
                    }
                }
                return@launch
            }
        }
    }
}
