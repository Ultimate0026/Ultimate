// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Ultrevo. See LICENSE and NOTICE.

package com.ultimate.macrobot.engine

import com.ultimate.macrobot.model.RuleGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupGateTest {
    @Test
    fun aGroupRestsWhenOneOfItsRulesIsFoundAndWakesAfterItsPause() {
        var now = 1_000L
        val forever = RuleGroup(name = "forever")
        val timed = RuleGroup(name = "timed", pauseS = 10)
        val resets = RuleGroup(name = "resets", resetOnRestart = true)
        val gate = GroupGate(listOf(forever, timed, resets)) { now }

        assertFalse(gate.isResting(null))
        assertFalse(gate.isResting(forever.id))
        assertNull(gate.found(null))
        assertNull(gate.found("unknown"))
        for (g in listOf(forever, timed, resets)) {
            assertSame(g, gate.found(g.id))
            assertTrue(gate.isResting(g.id))
        }
        now += 11_000
        assertFalse(gate.isResting(timed.id)) // its pause is over
        assertTrue(gate.isResting(forever.id))
        assertTrue(gate.isResting(resets.id))

        gate.restarted()
        assertTrue(gate.isResting(forever.id)) // only groups set to re-enable wake up
        assertFalse(gate.isResting(resets.id))
    }

    @Test
    fun aGroupThatRestedBeforeCanRestAgain() {
        var now = 0L
        val g = RuleGroup(name = "g", pauseS = 5, resetOnRestart = true)
        val gate = GroupGate(listOf(g)) { now }
        gate.found(g.id)
        gate.restarted()
        assertFalse(gate.isResting(g.id))
        gate.found(g.id)
        assertTrue(gate.isResting(g.id))
        now += 5_001
        assertFalse(gate.isResting(g.id))
        assertEquals(g.name, gate.found(g.id)?.name)
    }
}
