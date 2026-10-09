package app.foldcade

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import app.foldcade.api.plugin.LaunchFlag
import app.foldcade.api.plugin.PlayerExtra
import app.foldcade.api.plugin.PlayerIntent

/**
 * Turns a plugin intent into a start.
 * The read grant is applied when the plugin asked for it.
 * This function does not name a player or a save path.
 */
fun PlayerIntent.toAndroidIntent(): Intent {
    val intent = Intent(action)
    intent.component = ComponentName(packageName, componentClass)
    if (dataUri != null && mimeType != null) {
        intent.setDataAndType(Uri.parse(dataUri), mimeType)
    } else if (dataUri != null) {
        intent.data = Uri.parse(dataUri)
    } else if (mimeType != null) {
        intent.type = mimeType
    }
    if (grantReadUri) intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    flags.forEach { flag ->
        when (flag) {
            LaunchFlag.GrantWriteUri -> intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            LaunchFlag.NewTask -> intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            LaunchFlag.ClearTop -> intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            LaunchFlag.ClearTask -> intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            else -> Unit
        }
    }
    extras.forEach { extra ->
        when (extra) {
            is PlayerExtra.Text -> intent.putExtra(extra.key, extra.value)
            is PlayerExtra.Integer -> intent.putExtra(extra.key, extra.value)
            is PlayerExtra.LongValue -> intent.putExtra(extra.key, extra.value)
            is PlayerExtra.BooleanFlag -> intent.putExtra(extra.key, extra.value)
            else -> Unit
        }
    }
    return intent
}
