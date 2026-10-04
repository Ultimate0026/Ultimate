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
}
