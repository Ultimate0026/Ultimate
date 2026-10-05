// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Ultrevo. See LICENSE and NOTICE.

package com.ultimate.macrobot.engine

/** Lets each key through at most once per [minGapMs], so a rule that keeps matching can't flood a channel. */
class Throttle(private val minGapMs: Long, private val now: () -> Long = System::currentTimeMillis) {
    private val last = HashMap<String, Long>()

    @Synchronized
    fun ready(key: String): Boolean = now() - (last[key] ?: Long.MIN_VALUE / 2) >= minGapMs

    @Synchronized
    fun mark(key: String) {
        last[key] = now()
    }
}
