package app.foldcade

import android.app.Presentation
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.findViewTreeCompositionContext
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Moves the activity's one panel view above a leftover emulator Presentation. */
internal class PanelRecoveryWindow(
    private val activity: FoldcadeHomeActivity,
    private val home: FrameLayout,
    private val panel: ComposeView,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var presentation by mutableStateOf<RecoveryPresentation?>(null)
    private val lease = RecoveryLease(::show, ::dismiss) { delay, repair ->
        handler.postDelayed({ repair() }, delay)
    }

    fun update(wanted: Boolean, reclaim: Boolean = false) {
        lease.update(wanted, reclaim)
        if (!wanted) handler.removeCallbacksAndMessages(null)
    }

    fun close() = update(false)

    fun brightness(level: Float) {
        presentation?.window?.let { window ->
            window.attributes = window.attributes.apply { screenBrightness = level }
        }
    }

    /** Text input belongs to the presentation's window token while it holds the panel. */
    suspend fun takeKeyboardFocus() {
        val window = presentation ?: return
        window.takeFocus()
        // A bounded repair may replace the window during this focus handoff.
        withTimeoutOrNull(1_000) { snapshotFlow { presentation?.focused == true }.first { it } }
    }

    private fun show(): Boolean {
        val display = activity.display ?: return false
        if (!display.isValid || display.state != Display.STATE_ON ||
            display.flags and Display.FLAG_PRESENTATION == 0 ||
            display.flags and Display.FLAG_PRIVATE != 0
        ) return false
        // Start the composition in the activity window so its recomposer survives later replacements.
        if (!home.isAttachedToWindow) {
            home.post { activity.refreshRecoveryWindow(reclaim = true) }
            return false
        }
        val composition = home.findViewTreeCompositionContext() ?: return false
        panel.setParentCompositionContext(composition)
        val wantsFocus = presentation?.window?.attributes?.flags?.let {
            it and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0
        } == true
        val restoreFocus = panel.hasFocus()
        val savedChild = restoreFocus && activity.panelFocus.saveFocusedChild()
        // Replace the window directly; the panel need not visit its native container in between.
        removePresentation()
        val window = RecoveryPresentation(display, restoreFocus, savedChild)
        presentation = window
        window.setOnDismissListener {
            if (presentation === window) {
                presentation = null
                moveTo(home)
                close()
                activity.refreshRecoveryWindow()
            }
        }
        return try {
            window.show()
            brightness(activity.window.attributes.screenBrightness)
            if (wantsFocus) window.takeFocus()
            true
        } catch (failure: WindowManager.InvalidDisplayException) {
            failed(failure)
        } catch (failure: WindowManager.BadTokenException) {
            failed(failure)
        }
    }

    private fun failed(failure: RuntimeException): Boolean {
        Log.w("PanelRecovery", "Could not recover the panel window", failure)
        dismiss()
        return false
    }

    private fun moveTo(parent: ViewGroup) {
        (panel.parent as? ViewGroup)?.removeView(panel)
        parent.addView(panel, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    private fun removePresentation() {
        val old = presentation
        presentation = null
        old?.dismiss()
    }

    private fun dismiss() {
        removePresentation()
        if (panel.parent !== home) moveTo(home)
    }

    private inner class RecoveryPresentation(
        display: Display,
        private var restoreFocus: Boolean,
        private val savedChild: Boolean,
    ) : Presentation(activity, display) {
        var focused by mutableStateOf(false)
            private set

        override fun onWindowFocusChanged(hasFocus: Boolean) {
            super.onWindowFocusChanged(hasFocus)
            focused = hasFocus
            if (hasFocus && restoreFocus && presentation === this) {
                restoreFocus = false
                if (!savedChild || !activity.panelFocus.restoreFocusedChild()) activity.panelFocus.requestFocus()
            }
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            window?.apply {
                addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                setBackgroundDrawableResource(android.R.color.black)
                decorView.setViewTreeLifecycleOwner(activity)
                decorView.setViewTreeViewModelStoreOwner(activity)
                decorView.setViewTreeSavedStateRegistryOwner(activity)
                decorView.setViewTreeOnBackPressedDispatcherOwner(activity)
                insetsController?.hide(android.view.WindowInsets.Type.systemBars())
                insetsController?.systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            val root = FrameLayout(context)
            setContentView(root)
            moveTo(root)
        }

        fun takeFocus() {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
        }

        override fun dispatchTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) takeFocus()
            return super.dispatchTouchEvent(event)
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean =
            activity.dispatchFormKeyEvent(event) ?: (super.dispatchKeyEvent(event) || activity.dispatchKeyEvent(event))

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onBackPressed() = activity.onBackPressedDispatcher.onBackPressed()
    }
}
