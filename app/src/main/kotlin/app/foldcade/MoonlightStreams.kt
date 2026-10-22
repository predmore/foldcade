package app.foldcade

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import app.foldcade.gamestream.GameStreamClient
import app.foldcade.gamestream.GameStreamPairing
import app.foldcade.gamestream.HostApp
import app.foldcade.gamestream.PairOutcome
import app.foldcade.gamestream.PairedHost
import app.foldcade.language.DialogKind
import app.foldcade.language.HostScreen
import app.foldcade.language.PairFailure
import app.foldcade.language.pairFailedDialog
import app.foldcade.language.pairHostDialog
import app.foldcade.net.LanHttp
import app.foldcade.plugins.moonlight.MoonlightApp
import app.foldcade.plugins.moonlight.MoonlightLibrary
import app.foldcade.plugins.moonlight.MoonlightPlayer
import app.foldcade.plugins.moonlight.moonlightApp
import app.foldcade.plugins.moonlight.moonlightPosterUri
import java.io.File
import java.security.GeneralSecurityException
import java.security.ProviderException
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Moonlight's computers as Foldcade knows them: the watcher that asks how each
 * is, the ones Foldcade paired with itself, and the apps those list.
 *
 * Pairing makes this device a GameStream client of its own, next to
 * Moonlight, so a computer's apps list themselves without a shortcut each.
 * Moonlight still streams them: a listed app starts the same way a pinned one does.
 */
internal class MoonlightStreams(
    private val context: Context,
    private val store: SessionStore,
    private val scope: CoroutineScope,
    private val shell: () -> ShellController,
    private val library: () -> MoonlightLibrary?,
    /** Puts the library's catalog back on the shelf. Off the main thread. */
    private val refreshShelf: suspend () -> Unit,
) {
    @Volatile
    private var identity: GameStreamIdentity? = null

    @Volatile
    private var client: GameStreamClient = GameStreamClient(LanHttp(), store.gameStreamClientId())

    @Volatile
    private var paired: List<PairedStreamHost> = emptyList()

    private var pairing: Job? = null
    private val listing = Mutex()

    val hosts: MoonlightHosts = MoonlightHosts(
        context = context,
        client = { client },
        scope = scope,
        active = ::moonlightInstalled,
        publish = { shell().setMoonlightHosts(it) },
        onApps = ::listed,
    )

    /** Box art for tiles: Moonlight's own poster, then what a paired computer keeps. */
    private val artDir = File(context.cacheDir, "moonlight/art")

    /**
     * The pairings and apps saved last time, before any computer answers. Pairings
     * made with a key this device no longer has are dropped. Off the main thread.
     */
    suspend fun restore(names: Map<String, String>) {
        val found = try {
            GameStreamIdentity.existing()
        } catch (failure: GeneralSecurityException) {
            Log.w("Foldcade", "GameStream key unreadable", failure)
            null
        } catch (failure: ProviderException) {
            Log.w("Foldcade", "GameStream key unreadable", failure)
            null
        }
        found?.let(::use)
        paired = store.pairedStreamHosts(found?.fingerprint)
        val keys = paired.map { it.key }.toSet()
        val listed = store.listedStreamApps().filter { it.hostUuid.lowercase(Locale.ROOT) in keys }
        library()?.replaceListed(listed)
        hosts.knowPaired(paired)
        hosts.knowNames(names)
        val known = paired.associate { it.key to MoonlightHost(it.name, online = null) } +
            names.mapKeys { it.key.lowercase(Locale.ROOT) }.mapValues { MoonlightHost(it.value, online = null) }
        withContext(Dispatchers.Main.immediate) {
            shell().setPairedHosts(keys)
            shell().setMoonlightHosts(known)
        }
    }

    /**
     * Pairs with the computer [key]: shows a code for the user to type into Sunshine
     * or Apollo there, and waits. Main thread.
     */
    fun pair(key: String, screen: HostScreen) {
        pairing?.cancel()
        val name = shell().moonlightHosts[key]?.name ?: return
        pairing = scope.launch {
            val at = hosts.locate(key)
            val outcome = if (at == null) {
                PairOutcome.Unreachable
            } else {
                try {
                    val me = identity ?: GameStreamIdentity.load().also(::use)
                    val steps = GameStreamPairing(me)
                    val pin = steps.newPin()
                    withContext(Dispatchers.Main.immediate) { shell().present(pairHostDialog(name, pin, key, screen)) }
                    steps.pair(client.pairLink(at.address, at.httpsPort), pin)
                } catch (failure: GeneralSecurityException) {
                    Log.w("Foldcade", "GameStream pairing failed", failure)
                    PairOutcome.Refused
                } catch (failure: ProviderException) {
                    Log.w("Foldcade", "GameStream pairing failed", failure)
                    PairOutcome.Refused
                }
            }
            Log.i("Foldcade", "moonlight-pair $name ${outcome::class.simpleName}")
            withContext(Dispatchers.Main.immediate) {
                val dialog = shell().model.dialog
                if (dialog?.kind == DialogKind.PairHost && dialog.subject == key) shell().dismissDialog()
                when (outcome) {
                    is PairOutcome.Paired -> Unit
                    PairOutcome.WrongPin -> shell().present(pairFailedDialog(name, PairFailure.WrongPin, screen))
                    PairOutcome.Refused -> shell().present(pairFailedDialog(name, PairFailure.Refused, screen))
                    PairOutcome.Unreachable -> shell().present(pairFailedDialog(name, PairFailure.Unreachable, screen))
                }
            }
            if (outcome is PairOutcome.Paired && at != null) {
                val me = identity ?: return@launch
                keep(paired.filter { it.key != key } + PairedStreamHost(at.uuid, name, at.httpsPort, outcome.serverCertificate), me)
            }
        }
    }

    /** Not now on the code: the host drops its half on its own. Main thread. */
    fun cancelPairing() {
        pairing?.cancel()
        pairing = null
    }

    /**
     * Stops listing [key]'s apps. Its tiles leave with them, except games pinned
     * in Moonlight. The computer keeps this device in its client list until the
     * user removes it there: Sunshine has no way for a client to leave.
     */
    fun forget(key: String) {
        scope.launch {
            val me = identity ?: return@launch
            keep(paired.filter { it.key != key }, me)
            listing.withLock {
                val rest = store.listedStreamApps().filter { it.hostUuid.lowercase(Locale.ROOT) != key }
                store.setListedStreamApps(rest)
                library()?.replaceListed(rest)
            }
            File(artDir, key).deleteRecursively()
            refreshShelf()
        }
    }

    /**
     * Where [app]'s box art is: Moonlight's poster when it has one, else this
     * device's copy of the host's, fetched once from a paired computer. Off the main thread.
     */
    suspend fun poster(app: MoonlightApp): String? {
        moonlightPosterUri(app.hostUuid, app.appId).takeIf { moonlightPosterExists(context, it) }?.let { return it }
        val key = app.hostUuid.lowercase(Locale.ROOT)
        val file = File(File(artDir, key), "${app.appId}.png")
        if (file.isFile) return Uri.fromFile(file).toString()
        val host = paired.firstOrNull { it.key == key } ?: return null
        val at = hosts.lastAt(key) ?: return null
        val bytes = client.boxArt(PairedHost(at.address, host.httpsPort, host.certificate), app.appId) ?: return null
        return withContext(Dispatchers.IO) {
            file.parentFile?.mkdirs()
            val temp = File(file.parentFile, "${app.appId}.tmp")
            temp.writeBytes(bytes)
            if (temp.renameTo(file)) Uri.fromFile(file).toString() else null
        }
    }

    /** True for an address [poster] made for a host's box art, which only this app reads. */
    fun isHostArt(uri: String): Boolean {
        val path = Uri.parse(uri).takeIf { it.scheme == "file" }?.path ?: return false
        val file = File(path).canonicalFile
        return file.path.startsWith(artDir.canonicalPath + File.separator)
    }

    private fun use(found: GameStreamIdentity) {
        identity = found
        client = GameStreamClient(LanHttp(), store.gameStreamClientId(), found)
    }

    private suspend fun keep(next: List<PairedStreamHost>, me: GameStreamIdentity) {
        paired = next
        store.setPairedStreamHosts(me.fingerprint, next)
        hosts.knowPaired(next)
        val keys = next.map { it.key }.toSet()
        withContext(Dispatchers.Main.immediate) { shell().setPairedHosts(keys) }
    }

    /** A paired computer listed [apps]: they replace what it listed before. */
    private suspend fun listed(host: PairedStreamHost, apps: List<HostApp>) {
        if (paired.none { it.key == host.key }) return
        listing.withLock {
            val mine = apps.mapNotNull { moonlightApp(hostUuid = host.uuid, appId = it.id, label = it.title) }
            val others = store.listedStreamApps().filter { it.hostUuid.lowercase(Locale.ROOT) != host.key }
            val all = others + mine
            if (all == store.listedStreamApps()) return
            store.setListedStreamApps(all)
            library()?.replaceListed(all)
        }
        refreshShelf()
    }

    private fun moonlightInstalled(): Boolean = try {
        context.packageManager.getPackageInfo(MoonlightPlayer.OFFICIAL_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
