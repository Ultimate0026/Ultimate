// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Ultrevo. See LICENSE and NOTICE.

package com.ultimate.macrobot.engine

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import com.ultimate.macrobot.MacroBotApp
import com.ultimate.macrobot.data.DiscordWebhook
import com.ultimate.macrobot.model.Macro
import com.ultimate.macrobot.model.RunMode
import com.ultimate.macrobot.model.Step
import com.ultimate.macrobot.model.StepType
import com.ultimate.macrobot.model.WatchAction
import com.ultimate.macrobot.service.MacroAccessibilityService
import com.ultimate.macrobot.service.ScreenCaptureService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference
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
    private const val MIN_SCAN_MS = 100L
    private const val DEFAULT_SCAN_MS = 250L
    private const val WATCH_COOLDOWN_MS = 1500L

    /** Only one thing touches the screen at a time: the main macro or a watcher. */
    private val actionLock = Mutex()

    /** An error while running (for example the phone running out of memory) stops the macro and says so, instead of crashing the app. */
    private val failureHandler = CoroutineExceptionHandler { _, error ->
        if (error is CancellationException) return@CoroutineExceptionHandler
        Log.e("Ultrebo", "The macro stopped because of an error", error)
        val reason = if (error is OutOfMemoryError) "the phone ran out of memory" else error.javaClass.simpleName
        _status.value = ""
        appContext?.let { context ->
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Macro stopped: $reason", Toast.LENGTH_LONG).show()
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + failureHandler)
    private var appContext: Context? = null
    private var job: Job? = null

    /** Pause between screen checks; set from the running macro. */
    @Volatile private var scanMs = DEFAULT_SCAN_MS

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
        val steps = macro.steps.filter { it.enabled }
        val rules = macro.rules.filter { it.enabled && it.needsScreen }
        if (steps.isEmpty() && rules.isEmpty()) return "This macro has no enabled steps or rules."
        if (MacroAccessibilityService.instance == null) return "Enable the Ultrebo accessibility service first."
        val enabled = steps + rules
        if (enabled.any { it.needsScreen }) {
            if (enabled.any { it.isImage } && !ImageMatcher.ensureLoaded()) return "Image recognition failed to load."
            if (ScreenCaptureService.instance?.isReady != true) return "Grant screen capture first (needed for image and text steps and rules)."
            if (enabled.any { it.isImage && it.templateFile == null }) return "An image step or rule has no image picked."
            if (enabled.any { it.isText && it.text.isBlank() }) return "A text step or rule has no text entered."
            enabled.firstOrNull { it.needsScreen && it.notify }?.let {
                if (!DiscordNotifier.configured()) {
                    return "\"${it.title()}\" is set to send a screenshot to Discord, but no Discord webhook is set. " +
                        "Add one with the bell button on the home screen, or switch that option off."
                }
            }
        }
        stop()
        appContext = context.applicationContext
        scanMs = macro.scanIntervalMs.coerceAtLeast(MIN_SCAN_MS)
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
        scanMs = DEFAULT_SCAN_MS
        _running.value = true
        job = scope.launch {
            try {
                if (step.needsScreen && ScreenCaptureService.instance?.isReady != true) return@launch
                runSequenceStep(step)
            } finally {
                _running.value = false
                _status.value = ""
            }
        }
    }

    private suspend fun run(macro: Macro) = coroutineScope {
        val mainSteps = macro.ordered().filter { it.enabled }
        // Highest priority first: when several rules are on screen at once, the first in this list wins.
        val watchers = macro.orderedRules().filter { it.enabled && it.needsScreen }

        val gate = GroupGate(macro.groups)
        val mainRef = AtomicReference<Job>()
        fun startMain() { mainRef.set(launch { runMain(macro, mainSteps, gate) }) }
        startMain()

        val watcher = if (watchers.isEmpty()) null else launch {
            watchLoop(
                watchers,
                gate,
                stopMain = { mainRef.get().cancelAndJoin() },
                startMain = { startMain() },
            )
        }

        // Wait for the main macro to finish; a restart swaps in a fresh job, so keep following it.
        while (true) {
            val current = mainRef.get()
            current.join()
            if (current === mainRef.get() && !current.isCancelled) break
            delay(50)
        }
        watcher?.cancel()
    }

    private suspend fun runMain(macro: Macro, steps: List<Step>, gate: GroupGate) {
        if (steps.isEmpty()) awaitCancellation() // watchers only: keep running until stopped
        var round = 0
        while (currentCoroutineContext().isActive && (macro.loops == 0 || round < macro.loops)) {
            round++
            when (macro.mode) {
                RunMode.SEQUENCE -> {
                    if (round > 1) gate.restarted() // a new loop is a restart: groups set to re-enable wake up
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

    /**
     * Background loop for "always watching" image steps. When one is seen it takes the action lock
     * (so the main macro pauses between gestures), optionally taps, then either lets the main macro
     * carry on after the step's wait, or restarts it from the beginning.
     */
    private suspend fun watchLoop(
        watchers: List<Step>,
        gate: GroupGate,
        stopMain: suspend () -> Unit,
        startMain: () -> Unit,
    ) {
        val lastSeen = HashMap<String, Long>()
        while (true) {
            delay(scanMs)
            val active = watchers.filterNot { gate.isResting(it.groupId) }
            if (active.isEmpty()) continue // every group is resting: nothing to look for
            val frame = captureFrame()
            var hit: Pair<Step, Target>? = null
            try {
                val now = System.currentTimeMillis()
                for (w in active) {
                    if (now - (lastSeen[w.id] ?: 0L) < WATCH_COOLDOWN_MS) continue
                    val match = findTarget(frame, w) ?: continue
                    hit = w to match
                    break
                }
            } finally {
                frame.release()
            }
            val (step, match) = hit ?: continue

            _status.value = "Watcher: ${step.title()}"
            notifyFound(step, match)
            actionLock.withLock {
                if (step.tapOnSeen) perform(step, match.x, match.y)
                if (step.onSeen == WatchAction.RESTART) stopMain()
                delay(step.delayAfterMs)
            }
            if (step.onSeen == WatchAction.RESTART) startMain()
            lastSeen[step.id] = System.currentTimeMillis()
            val group = gate.found(step.groupId)
            if (group != null) _status.value = "Group ${group.name}: found, so the group stops looking"
            if (step.onSeen == WatchAction.RESTART) gate.restarted() // the macro started over
        }
    }

    private suspend fun runSequenceStep(step: Step) {
        _status.value = step.title()
        val times = step.repeat.coerceAtLeast(1)
        when (step.type) {
            StepType.TAP, StepType.SWIPE -> repeat(times) {
                performThenWait(step, step.x, step.y)
            }
            StepType.WAIT_IMAGE, StepType.TAP_IMAGE, StepType.WAIT_TEXT, StepType.TAP_TEXT -> {
                val match = waitForTarget(step) ?: return // not found in time: skip the step
                notifyFound(step, match)
                if (step.tapsTarget) {
                    repeat(times) {
                        performThenWait(step, match.x, match.y)
                    }
                } else {
                    delay(step.delayAfterMs)
                }
            }
        }
    }

    private suspend fun runReactiveCycle(steps: List<Step>) {
        val needsFrame = steps.any { it.needsScreen }
        val frame = if (needsFrame) captureFrame() else null
        try {
            for (step in steps) {
                currentCoroutineContext().ensureActive()
                when (step.type) {
                    StepType.TAP, StepType.SWIPE -> {
                        _status.value = step.title()
                        performThenWait(step, step.x, step.y)
                        return
                    }
                    StepType.WAIT_IMAGE, StepType.TAP_IMAGE, StepType.WAIT_TEXT, StepType.TAP_TEXT -> {
                        val match = if (frame != null) findTarget(frame, step) else null
                        if (match != null) {
                            _status.value = step.title()
                            notifyFound(step, match)
                            if (step.tapsTarget) {
                                repeat(step.repeat.coerceAtLeast(1)) {
                                    performThenWait(step, match.x, match.y)
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
            delay(scanMs)
        } finally {
            frame?.release()
        }
    }

    /** Where an image or text step's target is on this frame, or null when it is not visible. */
    private data class Target(val x: Int, val y: Int, val shot: Bitmap? = null)

    /** A step that asked for it was found: queue the screenshot it was found in for Discord (sent in the background). */
    private fun notifyFound(step: Step, target: Target) {
        if (!step.notify) return
        val macro = DiscordWebhook.cleanText(MacroBotApp.repo.active()?.name.orEmpty(), 60)
        val where = if (macro.isEmpty()) "" else " in \"$macro\""
        DiscordNotifier.send(step.id, "Ultrebo found \"${DiscordWebhook.cleanText(step.title(), 80)}\"$where", target.shot)
    }

    private fun findTarget(frame: ImageMatcher.Frame, step: Step): Target? = when {
        step.isImage -> {
            val file = step.templateFile?.let { MacroBotApp.repo.templatePath(it) }
            if (file == null) null else ImageMatcher.find(frame, file, step.threshold)?.let { Target(it.x, it.y, frame.bitmap) }
        }
        step.isText ->
            TextMatcher.find(frame.text, step.text, step.threshold.toDouble())?.let { Target(it.x, it.y, frame.bitmap) }
        else -> null
    }

    private suspend fun waitForTarget(step: Step): Target? {
        val deadline = System.currentTimeMillis() + step.timeoutMs
        while (true) {
            currentCoroutineContext().ensureActive()
            val frame = captureFrame()
            try {
                findTarget(frame, step)?.let { return it }
            } finally {
                frame.release()
            }
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0) return null
            delay(minOf(scanMs, remaining))
        }
    }

    private suspend fun captureFrame(): ImageMatcher.Frame {
        while (true) {
            currentCoroutineContext().ensureActive()
            val bmp = ScreenCaptureService.instance?.capture()
            if (bmp != null) return ImageMatcher.Frame(bmp)
            delay(DEFAULT_SCAN_MS)
        }
    }

    /**
     * Does the gesture, then waits so the next one starts [Step.durationMs] + [Step.delayAfterMs] after this
     * one began. Counting from when the gesture began keeps the gap steady even when the phone is slow to
     * start or report a tap.
     */
    private suspend fun performThenWait(step: Step, x: Int, y: Int) {
        var startedAt = 0L
        actionLock.withLock {
            startedAt = SystemClock.elapsedRealtime()
            perform(step, x, y)
        }
        delay(Pacing.remainingMs(startedAt, step.durationMs, step.delayAfterMs, SystemClock.elapsedRealtime()))
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
