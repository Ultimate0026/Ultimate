// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Ultrevo. See LICENSE and NOTICE.

package com.ultimate.macrobot.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class StepType(val label: String) {
    TAP("Tap"),
    SWIPE("Swipe"),
    WAIT_IMAGE("Wait for image"),
    TAP_IMAGE("Tap image"),
    WAIT_TEXT("Wait for text"),
    TAP_TEXT("Tap text"),
}

@Serializable
enum class RunMode(val label: String, val help: String) {
    SEQUENCE(
        "Sequence",
        "Runs every step once per loop, from the top of the list to the bottom.",
    ),
    REACTIVE(
        "Reactive",
        "Each cycle, runs only the first step in the list whose condition is met, " +
            "then starts over. Tap/Swipe steps are always met, so put them at the bottom " +
            "to act as a fallback. A \"Wait for image\" step that is visible holds back every " +
            "step below it.",
    ),
}

@Serializable
enum class WatchAction(val label: String) {
    CONTINUE("Pause, then carry on"),
    RESTART("Restart macro from the start"),
}

/**
 * Rules that share a group stop being checked together: once one of them is found, the rest rest too.
 * They are checked again when [pauseS] seconds have passed (if it is above 0) or, with [resetOnRestart],
 * when the macro restarts (it finishes a loop and starts over, or a rule restarts it).
 */
@Serializable
data class RuleGroup(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Group",
    val pauseS: Int = 0,
    val resetOnRestart: Boolean = false,
)

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
    /** Text steps: the words to look for on screen. */
    val text: String = "",
    /** Minimum match score / text similarity, 0..1 (image and text steps). */
    val threshold: Float = 0.8f,
    /** Sequence mode: how long to keep looking for the image. 0 = look once. */
    val timeoutMs: Long = 5000,
    val repeat: Int = 1,
    /** Set on rules: they watch the screen in the background for the whole run instead of running in order. */
    val watch: Boolean = false,
    /** What a watcher does when it sees its image. */
    val onSeen: WatchAction = WatchAction.CONTINUE,
    /** Watchers: tap the image when it is seen. */
    val tapOnSeen: Boolean = true,
    /** Rules: the [RuleGroup] this rule belongs to, if any. */
    val groupId: String? = null,
) {
    val isImage: Boolean get() = type == StepType.WAIT_IMAGE || type == StepType.TAP_IMAGE
    val isText: Boolean get() = type == StepType.WAIT_TEXT || type == StepType.TAP_TEXT

    /** Needs the screen to be captured (image or text recognition). */
    val needsScreen: Boolean get() = isImage || isText

    /** Taps what it finds (as opposed to only waiting for it). */
    val tapsTarget: Boolean get() = type == StepType.TAP_IMAGE || type == StepType.TAP_TEXT

    fun title(): String = name.ifBlank { type.label }

    /** How a rule reads in the Rules list: what it looks for, then what it does. */
    fun ruleSummary(): String {
        if (isImage && templateFile == null) return "no image picked"
        if (isText && text.isBlank()) return "no text entered"
        val target = if (isImage) "an image" else "\"$text\""
        val tap = if (tapOnSeen) "tap it, then " else ""
        return "When $target appears: $tap${onSeen.label.lowercase()}"
    }

    /** What the per-step Test button should run: a rule is tested as a plain find-and-tap (or find-only) step. */
    fun testable(): Step = when {
        !watch -> this
        isImage -> copy(type = if (tapOnSeen) StepType.TAP_IMAGE else StepType.WAIT_IMAGE, watch = false)
        isText -> copy(type = if (tapOnSeen) StepType.TAP_TEXT else StepType.WAIT_TEXT, watch = false)
        else -> this
    }

    fun summary(): String = when (type) {
        StepType.TAP -> "($x, $y)"
        StepType.SWIPE -> "($x, $y) -> ($x2, $y2)"
        StepType.WAIT_IMAGE, StepType.TAP_IMAGE ->
            when {
                templateFile == null -> "no image picked"
                watch -> "always watching - ${onSeen.label.lowercase()}"
                else -> "match >= ${"%.2f".format(threshold)}"
            }
        StepType.WAIT_TEXT, StepType.TAP_TEXT ->
            when {
                text.isBlank() -> "no text entered"
                watch -> "\"$text\" - always watching - ${onSeen.label.lowercase()}"
                else -> "\"$text\""
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
    /**
     * Always-watching detections. They run in the background for the whole run; when several are on
     * screen at once, the one with the lowest priority number is handled first.
     */
    val rules: List<Step> = emptyList(),
    val groups: List<RuleGroup> = emptyList(),
) {
    fun groupName(groupId: String?): String = groups.firstOrNull { it.id == groupId }?.name ?: ""

    /** Brings in groups that came with imported rules (a group with a name you already have is reused) and adds the rules. */
    fun withImported(newGroups: List<RuleGroup>, newRules: List<Step>): Macro {
        val merged = groups.toMutableList()
        val byName = HashMap<String, RuleGroup>()
        merged.forEach { byName[it.name.lowercase()] = it }
        val remap = HashMap<String, String>()
        for (g in newGroups) {
            val existing = byName[g.name.lowercase()]
            if (existing == null) {
                merged.add(g)
                byName[g.name.lowercase()] = g
                remap[g.id] = g.id
            } else {
                remap[g.id] = existing.id
            }
        }
        val remapped = newRules.map { r -> r.copy(groupId = r.groupId?.let { remap[it] }) }
        return copy(groups = merged, rules = rules + remapped)
    }

    fun withoutGroup(groupId: String): Macro = copy(
        groups = groups.filter { it.id != groupId },
        rules = rules.map { if (it.groupId == groupId) it.copy(groupId = null) else it },
    )

    /** Steps in execution order: priority ascending, ties keep list order. */
    fun ordered(): List<Step> =
        steps.withIndex().sortedWith(compareBy({ it.value.priority }, { it.index })).map { it.value }

    fun nextPriority(): Int = (steps.maxOfOrNull { it.priority } ?: 0) + 10

    /** Rules in the order they win: priority ascending, ties keep list order. */
    fun orderedRules(): List<Step> =
        rules.withIndex().sortedWith(compareBy({ it.value.priority }, { it.index })).map { it.value }

    fun nextRulePriority(): Int = (rules.maxOfOrNull { it.priority } ?: 0) + 10

    /** Older macros marked image/text steps "always watching" inside the step list; those are rules now. */
    fun migrated(): Macro {
        val moved = steps.filter { it.watch && it.needsScreen }
        val base = if (moved.isEmpty()) this else copy(
            steps = steps.filterNot { it.watch && it.needsScreen },
            rules = rules + moved.map { it.copy(repeat = 1) },
        )
        // A rule must not point at a group that no longer exists.
        val known = base.groups.map { it.id }.toSet()
        val fixed = base.rules.map { if (it.groupId != null && it.groupId !in known) it.copy(groupId = null) else it }
        return if (fixed == base.rules) base else base.copy(rules = fixed)
    }
}
