package app.foldcade

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import app.foldcade.language.FoldNotice

/**
 * Mirrors the device's notifications into R1. Android binds this once the user
 * turns on notification access for Foldcade. Nothing leaves the device.
 *
 * Ongoing notifications (music, downloads, services) and group summaries are
 * left out: they are status, not something to read and dismiss.
 */
class NoticeListener : NotificationListenerService() {
    private val main = Handler(Looper.getMainLooper())

    override fun onListenerConnected() {
        connected = this
        publish()
    }

    override fun onListenerDisconnected() {
        if (connected === this) connected = null
        main.post { (application as FoldcadeApp).setAndroidNotices(emptyList()) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) = publish()

    override fun onNotificationRemoved(sbn: StatusBarNotification?) = publish()

    private fun publish() {
        val shown = try {
            activeNotifications.orEmpty().filter(::worthShowing)
        } catch (_: SecurityException) {
            emptyList()
        }
        val notices = shown.map(::noticeOf)
        main.post { (application as FoldcadeApp).setAndroidNotices(notices) }
    }

    private fun worthShowing(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == packageName || sbn.isOngoing) return false
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return false
        val extras = sbn.notification.extras
        return !extras.getCharSequence(Notification.EXTRA_TITLE).isNullOrBlank() ||
            !extras.getCharSequence(Notification.EXTRA_TEXT).isNullOrBlank()
    }

    private fun noticeOf(sbn: StatusBarNotification): FoldNotice {
        val extras = sbn.notification.extras
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.takeIf { it.isNotBlank() } ?: label
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)
        return FoldNotice(
            id = sbn.key,
            title = title,
            body = text?.toString()?.lineSequence()?.firstOrNull { it.isNotBlank() }.orEmpty(),
            packageName = sbn.packageName,
            postedMillis = sbn.postTime,
            openable = sbn.notification.contentIntent != null,
            dismissable = sbn.isClearable,
        )
    }

    /**
     * Sends the notice's own intent onto [displayId]. When it opens an activity,
     * [placing] runs first, so the screen is marked taken before Foldcade leaves
     * it. A notice that cancels itself on open goes, as it would from the shade.
     */
    fun open(key: String, displayId: Int, placing: () -> Unit) {
        val sbn = activeNotifications.orEmpty().firstOrNull { it.key == key } ?: return
        val intent = sbn.notification.contentIntent ?: return
        if (intent.isActivity) placing()
        val options = ActivityOptions.makeBasic().apply { launchDisplayId = displayId }
        try {
            intent.send(this, 0, null, null, null, null, options.toBundle())
        } catch (_: PendingIntent.CanceledException) {
            return
        }
        if (sbn.notification.flags and Notification.FLAG_AUTO_CANCEL != 0) cancelNotification(key)
    }

    companion object {
        /** The bound listener, or null while access is off or Android has not bound it yet. */
        var connected: NoticeListener? = null
            private set

        fun component(context: Context): ComponentName = ComponentName(context, NoticeListener::class.java)

        fun granted(context: Context): Boolean =
            context.getSystemService(NotificationManager::class.java)
                ?.isNotificationListenerAccessGranted(component(context)) == true

        /** Android's switch for this listener, or the list of listeners where that page is missing. */
        fun accessIntents(context: Context): List<Intent> = listOf(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component(context).flattenToString()),
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS),
        )
    }
}
