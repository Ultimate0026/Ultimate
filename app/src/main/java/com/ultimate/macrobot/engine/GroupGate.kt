// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Ultrevo. See LICENSE and NOTICE.

package com.ultimate.macrobot.engine

import com.ultimate.macrobot.model.RuleGroup

/**
 * Which rule groups are resting. A group rests as soon as one of its rules is found. It is checked again when
 * its pause (if it has one) is over, or when the macro restarts and the group is set to re-enable then.
 */
class GroupGate(groups: List<RuleGroup>, private val clock: () -> Long = { System.currentTimeMillis() }) {
    private val byId = groups.associateBy { it.id }
    private val until = HashMap<String, Long>()

    @Synchronized
    fun isResting(groupId: String?): Boolean {
        if (groupId == null) return false
        val end = until[groupId] ?: return false
        if (end <= clock()) {
            until.remove(groupId)
            return false
        }
        return true
    }

    /** A rule in this group was found: rest the whole group. Returns the group, or null for an ungrouped rule. */
    @Synchronized
    fun found(groupId: String?): RuleGroup? {
        val group = byId[groupId ?: return null] ?: return null
        until[group.id] = if (group.pauseS > 0) clock() + group.pauseS * 1000L else Long.MAX_VALUE
        return group
    }

    /** The macro started over: groups set to re-enable on restart are checked again. */
    @Synchronized
    fun restarted() {
        val wake = until.keys.filter { byId[it]?.resetOnRestart == true }
        wake.forEach { until.remove(it) }
    }
}
