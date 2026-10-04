package com.ultimate.macrobot.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import com.ultimate.macrobot.engine.MacroRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Performs the taps and swipes, and hosts the floating controls (accessibility overlay windows
 * need no extra "draw over other apps" permission).
 */
class MacroAccessibilityService : AccessibilityService() {
    lateinit var overlay: OverlayController
        private set

    override fun onServiceConnected() {
        super.onServiceConnected()
        overlay = OverlayController(this)
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        MacroRunner.stop()
        MacroRunner.setRecording(false)
        if (::overlay.isInitialized) overlay.destroy()
        instance = null
        return super.onUnbind(intent)
    }

    suspend fun tap(x: Int, y: Int, durationMs: Long): Boolean =
        dispatch(Path().apply { moveTo(x.toFloat(), y.toFloat()) }, durationMs)

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long): Boolean =
        dispatch(
            Path().apply {
                moveTo(x1.toFloat(), y1.toFloat())
                lineTo(x2.toFloat(), y2.toFloat())
            },
            durationMs,
        )

    private suspend fun dispatch(path: Path, durationMs: Long): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val stroke = GestureDescription.StrokeDescription(path, 0, durationMs.coerceIn(1, 60_000))
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val started = dispatchGesture(
                gesture,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(false)
                    }
                },
                null,
            )
            if (!started && cont.isActive) cont.resume(false)
        }
    }

    companion object {
        @Volatile var instance: MacroAccessibilityService? = null
    }
}
