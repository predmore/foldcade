package app.foldcade.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import app.foldcade.language.MotionStyle
import app.foldcade.language.TiltFollower
import kotlin.math.abs

/** How far the Thor has moved, as shares of [MotionStyle.tiltPx]. Home when nothing reads it. */
internal val LocalTilt = staticCompositionLocalOf<State<Offset>> { mutableStateOf(Offset.Zero) }

/**
 * The Thor's lean from the way it was lately held, while [enabled] and the theme slides
 * anything. The sensor is only listened to then: otherwise the layers are home and nothing
 * wakes. A device without a gyroscope has no game rotation vector, and its layers stay home.
 */
@Composable
internal fun rememberTilt(enabled: Boolean, motion: MotionStyle): State<Offset> {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(enabled, motion.tiltDegrees, motion.durationTiltRecenter, motion.tiltPx) {
        tilt.value = Offset.Zero
        val sensors = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val rotation = sensors?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        if (!enabled || motion.tiltPx <= 0f || sensors == null || rotation == null) return@DisposableEffect onDispose { }
        val turns = context.display.rotation
        val follower = TiltFollower(motion.tiltDegrees, motion.durationTiltRecenter / 1000f, screenTurns = turns)
        // Moves under a quarter pixel are not drawn, so a Thor at rest draws no frames.
        val step = TILT_STEP_PX / motion.tiltPx
        val quaternion = FloatArray(4)
        var last = 0L
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getQuaternionFromVector(quaternion, event.values)
                val seconds = if (last == 0L) 0f else (event.timestamp - last) / 1_000_000_000f
                last = event.timestamp
                follower.follow(quaternion[0], quaternion[1], quaternion[2], quaternion[3], seconds)
                val shown = tilt.value
                if (abs(follower.x - shown.x) >= step || abs(follower.y - shown.y) >= step) {
                    tilt.value = Offset(follower.x, follower.y)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }
        sensors.registerListener(listener, rotation, SensorManager.SENSOR_DELAY_GAME)
        onDispose {
            sensors.unregisterListener(listener)
            tilt.value = Offset.Zero
        }
    }
    return tilt
}

/**
 * Slides this layer [share] of [MotionStyle.tiltPx] with the Thor's lean. A [cover]
 * layer fills the screen, so it is drawn just large enough that no edge shows.
 */
internal fun Modifier.tilted(tilt: State<Offset>, motion: MotionStyle, share: Float, cover: Boolean = false): Modifier =
    graphicsLayer {
        val lean = tilt.value
        translationX = lean.x * motion.tiltPx * share
        translationY = lean.y * motion.tiltPx * share
        if (cover) {
            val drawn = motion.tiltCover(minOf(size.width, size.height))
            scaleX = drawn
            scaleY = drawn
        }
    }

/** The smallest move worth a frame, in pixels. */
private const val TILT_STEP_PX = 0.25f
