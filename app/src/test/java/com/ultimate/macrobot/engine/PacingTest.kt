// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Ultrevo. See LICENSE and NOTICE.

package com.ultimate.macrobot.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class PacingTest {
    @Test
    fun `lag in finishing the gesture is taken out of the wait`() {
        // A 60 ms tap then 500 ms: the next one is due 560 ms after the start.
        assertEquals(560, Pacing.remainingMs(startedAt = 1000, durationMs = 60, delayAfterMs = 500, now = 1000))
        // The system took 40 ms to report the tap finished, so only 520 ms are left.
        assertEquals(520, Pacing.remainingMs(startedAt = 1000, durationMs = 60, delayAfterMs = 500, now = 1040))
    }

    @Test
    fun `never waits a negative time`() {
        assertEquals(0, Pacing.remainingMs(startedAt = 1000, durationMs = 60, delayAfterMs = 100, now = 5000))
    }

    @Test
    fun `bad values are kept in range`() {
        assertEquals(1, Pacing.remainingMs(startedAt = 0, durationMs = -5, delayAfterMs = -10, now = 0))
        assertEquals(60_000, Pacing.remainingMs(startedAt = 0, durationMs = 999_999, delayAfterMs = 0, now = 0))
    }
}
