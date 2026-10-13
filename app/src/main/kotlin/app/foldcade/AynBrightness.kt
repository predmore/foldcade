package app.foldcade

import android.os.IBinder
import android.os.Parcel
import android.util.Log

/**
 * A panel's brightness through AYN's own settings service. Android's setting
 * reaches only the default display. AYN's menu sets the other one with
 * `DisplayManager.setBrightness`, which needs a system permission, and the
 * firmware's `SettingsController` service (in com.odin.settings) makes that call
 * for any app. Levels are 0 to 255, a straight line onto the panel's range.
 * Off the Thor the service is missing and every call does nothing.
 */
internal object AynBrightness {
    private const val TAG = "AynBrightness"
    private const val SERVICE = "SettingsController"

    /** Display id, level, temporary. Replies with the level. */
    private const val SET = 16386

    /** Display id. Replies with the level, or -1. */
    private const val GET = 16388

    @Volatile private var service: IBinder? = null

    /** Stores [level] on [displayId], as AYN's menu does. A blocking call, so not on the main thread. */
    fun set(displayId: Int, level: Int): Boolean = call(SET) {
        writeInt(displayId)
        writeInt(level)
        writeBoolean(false)
    } == level

    /** The level [displayId] holds, 0 to 255. Null off the Thor. */
    fun get(displayId: Int): Int? = call(GET) { writeInt(displayId) }?.takeIf { it >= 0 }

    /** Both calls reply with one level. */
    private fun call(code: Int, write: Parcel.() -> Unit): Int? {
        val binder = binder() ?: return null
        val request = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            // The service reads its arguments straight off, with no interface token.
            request.write()
            if (!binder.transact(code, request, reply, 0)) return null
            reply.readException()
            reply.readInt()
        } catch (failure: Exception) {
            Log.w(TAG, "SettingsController call $code failed", failure)
            null
        } finally {
            reply.recycle()
            request.recycle()
        }
    }

    /** Looked up again once the binder dies, in case the settings process restarted. */
    private fun binder(): IBinder? = service?.takeIf { it.isBinderAlive } ?: try {
        Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, SERVICE) as IBinder?
    } catch (_: ReflectiveOperationException) {
        null
    }.also { service = it }
}
