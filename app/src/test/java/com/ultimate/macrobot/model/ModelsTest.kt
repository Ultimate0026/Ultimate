// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Ultrevo. See LICENSE and NOTICE.

package com.ultimate.macrobot.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {
    private fun step(name: String, priority: Int) = Step(name = name, priority = priority)

    @Test
    fun orderedSortsByPriorityThenListPosition() {
        val macro = Macro(steps = listOf(step("c", 30), step("a", 10), step("b1", 20), step("b2", 20)))
        assertEquals(listOf("a", "b1", "b2", "c"), macro.ordered().map { it.name })
    }

    @Test
    fun nextPriorityIsAfterTheHighestExisting() {
        assertEquals(10, Macro().nextPriority())
        assertEquals(40, Macro(steps = listOf(step("a", 30), step("b", 5))).nextPriority())
    }

    @Test
    fun macroSurvivesJsonRoundTrip() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val macro = Macro(
            name = "Farm",
            mode = RunMode.REACTIVE,
            steps = listOf(
                Step(name = "Start wave", type = StepType.TAP_IMAGE, templateFile = "x.png", threshold = 0.9f),
                Step(name = "Swipe", type = StepType.SWIPE, x = 1, y = 2, x2 = 3, y2 = 4),
            ),
        )
        assertEquals(macro, json.decodeFromString<Macro>(json.encodeToString(macro)))
    }

    @Test
    fun oldSavedFilesWithMissingFieldsStillLoad() {
        val json = Json { ignoreUnknownKeys = true }
        val macro = json.decodeFromString<Macro>("""{"name":"Old","steps":[{"type":"TAP","x":5,"y":6}]}""")
        assertEquals(500L, macro.steps.single().delayAfterMs)
        assertEquals(5, macro.steps.single().x)
    }

    @Test
    fun watcherSettingsDefaultOffForOldFiles() {
        val json = Json { ignoreUnknownKeys = true }
        val step = json.decodeFromString<Macro>("""{"steps":[{"type":"TAP_IMAGE"}]}""").steps.single()
        assertEquals(false, step.watch)
        assertEquals(WatchAction.CONTINUE, step.onSeen)
        assertEquals(true, step.tapOnSeen)
    }

    private fun rule(name: String, priority: Int, text: String = "hi") =
        Step(name = name, type = StepType.TAP_TEXT, text = text, priority = priority, watch = true)

    @Test
    fun rulesAreOrderedByPriorityNotListPosition() {
        val macro = Macro(rules = listOf(rule("low", 30), rule("high", 5), rule("tie1", 20), rule("tie2", 20)))
        assertEquals(listOf("high", "tie1", "tie2", "low"), macro.orderedRules().map { it.name })
        assertEquals(40, macro.nextRulePriority())
        assertEquals(10, Macro().nextRulePriority())
    }

    @Test
    fun rulesSurviveJsonRoundTripAndOldFilesHaveNone() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val macro = Macro(steps = listOf(step("tap", 10)), rules = listOf(rule("pop-up", 10, "I'm here")))
        assertEquals(macro, json.decodeFromString<Macro>(json.encodeToString(macro)))
        assertEquals(emptyList<Step>(), json.decodeFromString<Macro>("""{"steps":[{"type":"TAP"}]}""").rules)
    }

    @Test
    fun oldAlwaysWatchingStepsBecomeRules() {
        val json = Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString<Macro>(
            """{"steps":[{"type":"TAP","priority":5},
                {"type":"TAP_TEXT","text":"hi","watch":true,"priority":20},
                {"type":"TAP_IMAGE","templateFile":"a.png","watch":true,"priority":10}]}""",
        )
        val macro = old.migrated()
        assertEquals(listOf(StepType.TAP), macro.steps.map { it.type })
        assertEquals(listOf(StepType.TAP_IMAGE, StepType.TAP_TEXT), macro.orderedRules().map { it.type })
        assertEquals(macro, macro.migrated()) // nothing left to move the second time
    }

    @Test
    fun ruleSummaryAndTestStep() {
        val r = Step(type = StepType.TAP_TEXT, text = "I'm here", watch = true, onSeen = WatchAction.RESTART)
        assertEquals("When \"I'm here\" appears: tap it, then restart macro from the start", r.ruleSummary())
        assertEquals("no image picked", Step(type = StepType.TAP_IMAGE, watch = true).ruleSummary())
        assertEquals(StepType.TAP_TEXT, r.testable().type)
        assertEquals(false, r.testable().watch)
        val quiet = r.copy(tapOnSeen = false)
        assertEquals(StepType.WAIT_TEXT, quiet.testable().type)
    }

    @Test
    fun groupsSurviveJsonAndStaleGroupIdsAreDropped() {
        val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
        val g = RuleGroup(name = "A", pauseS = 12, resetOnRestart = true)
        val macro = Macro(
            groups = listOf(g),
            rules = listOf(rule("x", 10).copy(groupId = g.id), rule("y", 20).copy(groupId = "gone")),
        )
        val again = json.decodeFromString<Macro>(json.encodeToString(macro)).migrated()
        assertEquals(g, again.groups.single())
        assertEquals(g.id, again.rules[0].groupId)
        assertEquals(null, again.rules[1].groupId)
        assertEquals("A", again.groupName(g.id))
        assertEquals("", again.groupName("nope"))
        assertEquals(emptyList<RuleGroup>(), json.decodeFromString<Macro>("""{"steps":[]}""").groups)
    }

    @Test
    fun deletingAGroupKeepsItsRules() {
        val g = RuleGroup(name = "A")
        val macro = Macro(groups = listOf(g), rules = listOf(rule("x", 10).copy(groupId = g.id)))
        val after = macro.withoutGroup(g.id)
        assertEquals(emptyList<RuleGroup>(), after.groups)
        assertEquals(1, after.rules.size)
        assertEquals(null, after.rules[0].groupId)
    }

    @Test
    fun importedGroupsAreMergedByName() {
        val mine = RuleGroup(name = "pop-ups", pauseS = 5)
        val theirs = RuleGroup(name = "Pop-ups", pauseS = 30)
        val other = RuleGroup(name = "Daily")
        val macro = Macro(groups = listOf(mine), rules = listOf(rule("old", 10)))
        val result = macro.withImported(
            listOf(theirs, other),
            listOf(rule("a", 20).copy(groupId = theirs.id), rule("b", 30).copy(groupId = other.id), rule("c", 40)),
        )
        assertEquals(listOf("pop-ups", "Daily"), result.groups.map { it.name })
        assertEquals(5, result.groups[0].pauseS) // the group you already had is reused as it is
        assertEquals(listOf(null, mine.id, other.id, null), result.rules.map { it.groupId })
        assertEquals(4, result.rules.size)
    }
}
