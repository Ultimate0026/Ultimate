package com.ultimate.macrobot.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class StepType(val label: String) {
    TAP("Tap"),
    SWIPE("Swipe"),
    WAIT_IMAGE("Wait for image"),
    TAP_IMAGE("Tap image"),
}

@Serializable
enum class RunMode(val label: String, val help: String) {
    SEQUENCE(
        "Sequence",
        "Runs every step once per loop, from lowest priority number to highest.",
    ),
    REACTIVE(
        "Reactive",
        "Each cycle, runs only the first step (lowest priority number) whose condition is met, " +
            "then starts over. Tap/Swipe steps are always met, so give them the highest number " +
            "to act as a fallback. A \"Wait for image\" step that is visible holds back every " +
            "step below it.",
    ),
}

@Serializable
enum class WatchAction(val label: String) {
    CONTINUE("Pause, then carry on"),
    RESTART("Restart macro from the start"),
}

@Serializable
data class Step(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val type: StepType = StepType.TAP,
    val x: Int = 0,
    val y: Int = 0,
    /** Swipe end point. */
    val x2: Int = 0,
    val y2: Int = 0,
    /** Press time for a tap, travel time for a swipe. */
    val durationMs: Long = 60,
    /** Pause after this step (after each repeat). */
    val delayAfterMs: Long = 500,
    /** Lower number runs first. */
    val priority: Int = 0,
    val enabled: Boolean = true,
    /** File name inside the templates folder (image steps). */
    val templateFile: String? = null,
    /** Minimum match score, 0..1 (image steps). */
    val threshold: Float = 0.85f,
    /** Sequence mode: how long to keep looking for the image. 0 = look once. */
    val timeoutMs: Long = 5000,
    val repeat: Int = 1,
    /** Image steps: watch the screen in the background for the whole run instead of running in order. */
    val watch: Boolean = false,
    /** What a watcher does when it sees its image. */
    val onSeen: WatchAction = WatchAction.CONTINUE,
    /** Watchers: tap the image when it is seen. */
    val tapOnSeen: Boolean = true,
) {
    val needsImage: Boolean get() = type == StepType.WAIT_IMAGE || type == StepType.TAP_IMAGE

    fun title(): String = name.ifBlank { type.label }

    fun summary(): String = when (type) {
        StepType.TAP -> "($x, $y)"
        StepType.SWIPE -> "($x, $y) -> ($x2, $y2)"
        StepType.WAIT_IMAGE, StepType.TAP_IMAGE ->
            when {
                templateFile == null -> "no image picked"
                watch -> "always watching - ${onSeen.label.lowercase()}"
                else -> "match >= ${"%.2f".format(threshold)}"
            }
    }
}

@Serializable
data class Macro(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "New macro",
    val mode: RunMode = RunMode.SEQUENCE,
    /** Number of loops (sequence) or cycles (reactive). 0 = run until stopped. */
    val loops: Int = 0,
    val loopDelayMs: Long = 1000,
    /** How often image steps look at the screen (reactive idle wait / sequence polling). */
    val scanIntervalMs: Long = 1000,
    val steps: List<Step> = emptyList(),
) {
    /** Steps in execution order: priority ascending, ties keep list order. */
    fun ordered(): List<Step> =
        steps.withIndex().sortedWith(compareBy({ it.value.priority }, { it.index })).map { it.value }

    fun nextPriority(): Int = (steps.maxOfOrNull { it.priority } ?: 0) + 10
}
