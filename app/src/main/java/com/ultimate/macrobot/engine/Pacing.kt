// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Ultrevo. See LICENSE and NOTICE.

package com.ultimate.macrobot.engine

/** Keeps the gap between repeated taps steady. */
object Pacing {
    /**
     * How much longer to wait after a gesture that began at [startedAt] so that the next one begins
     * exactly [durationMs] + [delayAfterMs] after it. The system's own lag in starting and finishing
     * a gesture (it varies from tap to tap) is part of that time instead of being added to it.
     */
    fun remainingMs(startedAt: Long, durationMs: Long, delayAfterMs: Long, now: Long): Long {
        val due = startedAt + durationMs.coerceIn(1, 60_000) + delayAfterMs.coerceAtLeast(0)
        return (due - now).coerceAtLeast(0)
    }
}
