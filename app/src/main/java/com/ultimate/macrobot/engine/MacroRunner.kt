package com.ultimate.macrobot.engine

import android.content.Context
import com.ultimate.macrobot.MacroBotApp
import com.ultimate.macrobot.model.Macro
import com.ultimate.macrobot.model.RunMode
import com.ultimate.macrobot.model.Step
import com.ultimate.macrobot.model.StepType
import com.ultimate.macrobot.service.MacroAccessibilityService
import com.ultimate.macrobot.service.ScreenCaptureService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Image step waiting for the user to crop a region from the screen. */
data class PendingTemplate(val macroId: String, val stepId: String)

/** Runs macros and holds the global run/record state shown in the app and the floating bubble. */
object MacroRunner {
    private const val POLL_MS = 250L
    private const val IDLE_REACTIVE_MS = 300L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _recording = MutableStateFlow(false)
    val recording: StateFlow<Boolean> = _recording
    fun setRecording(value: Boolean) { _recording.value = value }

    private val _pendingTemplate = MutableStateFlow<PendingTemplate?>(null)
    val pendingTemplate: StateFlow<PendingTemplate?> = _pendingTemplate
    fun setPendingTemplate(value: PendingTemplate?) { _pendingTemplate.value = value }

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status

    /** Starts the active macro. Returns an error message, or null when started. */
    fun start(context: Context): String? {
        val macro = MacroBotApp.repo.active() ?: return "Open a macro first."
        val enabled = macro.steps.filter { it.enabled }
        if (enabled.isEmpty()) return "This macro has no enabled steps."
        if (MacroAccessibilityService.instance == null) return "Enable the MacroBot accessibility service first."
        if (enabled.any { it.needsImage }) {
            if (!ImageMatcher.ensureLoaded()) return "Image recognition failed to load."
            if (ScreenCaptureService.instance?.isReady != true) return "Grant screen capture first (needed for image steps)."
            if (enabled.any { it.needsImage && it.templateFile == null }) return "An image step has no image picked."
        }
        stop()
        _running.value = true
        job = scope.launch {
            try {
                run(macro)
            } finally {
                _running.value = false
                _status.value = ""
            }
        }
        return null
    }

    fun stop() {
        job?.cancel()
        job = null
        _running.value = false
    }

    /** Runs a single step once (the per-step test button). */
    fun testStep(step: Step) {
        if (_running.value) return
        _running.value = true
        job = scope.launch {
            try {
                if (step.needsImage && ScreenCaptureService.instance?.isReady != true) return@launch
                runSequenceStep(step)
            } finally {
                _running.value = false
                _status.value = ""
            }
        }
    }

    private suspend fun run(macro: Macro) {
        val steps = macro.ordered().filter { it.enabled }
        var round = 0
        while (currentCoroutineContext().isActive && (macro.loops == 0 || round < macro.loops)) {
            round++
            when (macro.mode) {
                RunMode.SEQUENCE -> {
                    for (step in steps) {
                        currentCoroutineContext().ensureActive()
                        runSequenceStep(step)
                    }
                    delay(macro.loopDelayMs)
                }
                RunMode.REACTIVE -> runReactiveCycle(steps)
            }
        }
    }

    private suspend fun runSequenceStep(step: Step) {
        _status.value = step.title()
        val times = step.repeat.coerceAtLeast(1)
        when (step.type) {
            StepType.TAP, StepType.SWIPE -> repeat(times) {
                perform(step, step.x, step.y)
                delay(step.delayAfterMs)
            }
            StepType.WAIT_IMAGE, StepType.TAP_IMAGE -> {
                val match = waitForImage(step) ?: return // not found in time: skip the step
                if (step.type == StepType.TAP_IMAGE) {
                    repeat(times) {
                        perform(step, match.x, match.y)
                        delay(step.delayAfterMs)
                    }
                } else {
                    delay(step.delayAfterMs)
                }
            }
        }
    }

    private suspend fun runReactiveCycle(steps: List<Step>) {
        val needsFrame = steps.any { it.needsImage }
        val frame = if (needsFrame) captureFrame() else null
        try {
            for (step in steps) {
                currentCoroutineContext().ensureActive()
                when (step.type) {
                    StepType.TAP, StepType.SWIPE -> {
                        _status.value = step.title()
                        perform(step, step.x, step.y)
                        delay(step.delayAfterMs)
                        return
                    }
                    StepType.WAIT_IMAGE, StepType.TAP_IMAGE -> {
                        val file = step.templateFile?.let { MacroBotApp.repo.templatePath(it) }
                        val match = if (frame != null && file != null) {
                            ImageMatcher.find(frame, file, step.threshold)
                        } else {
                            null
                        }
                        if (match != null) {
                            _status.value = step.title()
                            if (step.type == StepType.TAP_IMAGE) {
                                repeat(step.repeat.coerceAtLeast(1)) {
                                    perform(step, match.x, match.y)
                                    delay(step.delayAfterMs)
                                }
                            } else {
                                delay(step.delayAfterMs)
                            }
                            return
                        }
                    }
                }
            }
            _status.value = "Waiting..."
            delay(IDLE_REACTIVE_MS)
        } finally {
            frame?.release()
        }
    }

    private suspend fun waitForImage(step: Step): ImageMatcher.Match? {
        val file = step.templateFile?.let { MacroBotApp.repo.templatePath(it) } ?: return null
        val deadline = System.currentTimeMillis() + step.timeoutMs
        while (true) {
            currentCoroutineContext().ensureActive()
            val frame = captureFrame()
            try {
                ImageMatcher.find(frame, file, step.threshold)?.let { return it }
            } finally {
                frame.release()
            }
            if (System.currentTimeMillis() >= deadline) return null
            delay(POLL_MS)
        }
    }

    private suspend fun captureFrame(): ImageMatcher.Frame {
        while (true) {
            currentCoroutineContext().ensureActive()
            val bmp = ScreenCaptureService.instance?.capture()
            if (bmp != null) return ImageMatcher.Frame(bmp)
            delay(POLL_MS)
        }
    }

    private suspend fun perform(step: Step, x: Int, y: Int) {
        val svc = MacroAccessibilityService.instance
        if (svc == null) {
            stop()
            return
        }
        if (step.type == StepType.SWIPE) {
            svc.swipe(step.x, step.y, step.x2, step.y2, step.durationMs)
        } else {
            svc.tap(x, y, step.durationMs)
        }
    }
}
