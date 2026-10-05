// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Ultrevo. See LICENSE and NOTICE.

package com.ultimate.macrobot.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThrottleTest {
    @Test
    fun aKeyIsReadyAgainOnlyAfterTheGap() {
        var now = 1_000L
        val throttle = Throttle(5_000) { now }
        assertTrue(throttle.ready("claim"))
        throttle.mark("claim")
        assertFalse(throttle.ready("claim"))
        assertTrue(throttle.ready("afk")) // other keys are counted separately
        now += 4_999
        assertFalse(throttle.ready("claim"))
        now += 1
        assertTrue(throttle.ready("claim"))
    }

    @Test
    fun aKeyThatWasNeverMarkedIsReadyEvenAtTimeZero() {
        assertTrue(Throttle(5_000) { 0L }.ready("x"))
    }
}
