// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Ultrevo. See LICENSE and NOTICE.

package com.ultimate.macrobot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ultimate.macrobot.model.RuleGroup
import com.ultimate.macrobot.model.Step
import com.ultimate.macrobot.model.StepType
import com.ultimate.macrobot.model.WatchAction

/** Whole-number field that reports a value only when the text parses. */
@Composable
fun NumField(label: String, value: Long, modifier: Modifier = Modifier, onValue: (Long) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; it.toLongOrNull()?.let(onValue) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

/** The four things a step can be; image/text steps tap or only wait depending on a switch. */
private enum class Kind(val label: String) {
    TAP("Tap"),
    SWIPE("Swipe"),
    IMAGE("Find image"),
    TEXT("Find text"),
}

private fun kindOf(step: Step): Kind = when {
    step.isImage -> Kind.IMAGE
    step.isText -> Kind.TEXT
    step.type == StepType.SWIPE -> Kind.SWIPE
    else -> Kind.TAP
}

private fun typeFor(kind: Kind, tap: Boolean): StepType = when (kind) {
    Kind.TAP -> StepType.TAP
    Kind.SWIPE -> StepType.SWIPE
    Kind.IMAGE -> if (tap) StepType.TAP_IMAGE else StepType.WAIT_IMAGE
    Kind.TEXT -> if (tap) StepType.TAP_TEXT else StepType.WAIT_TEXT
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StepDialog(
    initial: Step,
    onDismiss: () -> Unit,
    onSave: (Step) -> Unit,
    onPickImage: (Step) -> Unit,
    rule: Boolean = false,
    groups: List<RuleGroup> = emptyList(),
) {
    var step by remember { mutableStateOf(initial) }
    var threshold by remember { mutableStateOf(initial.threshold.toString()) }
    var advanced by remember { mutableStateOf(false) }

    fun current(): Step = step.copy(
        threshold = threshold.toFloatOrNull()?.coerceIn(0.1f, 1f) ?: step.threshold,
        watch = rule && step.needsScreen, // a rule always watches; a plain step never does
        repeat = if (rule) 1 else step.repeat,
    )

    val kind = kindOf(step)
    val finds = step.needsScreen
    val isWatcher = finds && rule
    val tapsSomething = when {
        kind == Kind.TAP || kind == Kind.SWIPE -> true
        isWatcher -> step.tapOnSeen
        else -> step.tapsTarget
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rule) "Edit rule" else "Edit step") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = step.name,
                    onValueChange = { step = step.copy(name = it) },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                // --- What the step does
                Text(if (rule) "What it looks for" else "What it does", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // A rule only ever watches for a picture or for text.
                    Kind.entries.filter { !rule || it == Kind.IMAGE || it == Kind.TEXT }.forEach { k ->
                        FilterChip(
                            selected = kind == k,
                            onClick = {
                                val tap = step.tapsTarget || !step.needsScreen
                                step = step.copy(type = typeFor(k, tap))
                            },
                            label = { Text(k.label) },
                        )
                    }
                }
                Text(
                    when (kind) {
                        Kind.TAP -> "Taps one fixed spot on the screen."
                        Kind.SWIPE -> "Drags from one spot to another."
                        Kind.IMAGE -> "Looks for a picture you choose, wherever it appears."
                        Kind.TEXT -> "Looks for words you type, wherever they appear."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )

                // --- Where / what to look for
                when (kind) {
                    Kind.TAP, Kind.SWIPE -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField("X", step.x.toLong(), Modifier.weight(1f)) { step = step.copy(x = it.toInt()) }
                            NumField("Y", step.y.toLong(), Modifier.weight(1f)) { step = step.copy(y = it.toInt()) }
                        }
                        if (kind == Kind.SWIPE) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                NumField("End X", step.x2.toLong(), Modifier.weight(1f)) { step = step.copy(x2 = it.toInt()) }
                                NumField("End Y", step.y2.toLong(), Modifier.weight(1f)) { step = step.copy(y2 = it.toInt()) }
                            }
                        }
                    }
                    Kind.IMAGE -> {
                        OutlinedButton(onClick = { onPickImage(current()) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (step.templateFile == null) "Pick image from screen" else "Re-pick image from screen")
                        }
                        Text(
                            "Ultrebo goes to the background. Open your game, press CROP on the floating " +
                                "bar and drag a box around the button or icon. A thumbnail then shows on the step.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Kind.TEXT -> {
                        OutlinedTextField(
                            value = step.text,
                            onValueChange = { step = step.copy(text = it) },
                            label = { Text("Text to look for") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Capital letters, spaces and punctuation are ignored. Latin letters and " +
                                "numbers only (English and similar).",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                // --- Image / text options
                if (finds) {
                    if (!rule) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Tap it when found", Modifier.weight(1f))
                            Switch(
                                checked = step.tapsTarget,
                                onCheckedChange = { step = step.copy(type = typeFor(kind, it)) },
                            )
                        }
                    }

                    if (!rule) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Then start the macro over from the first step", Modifier.weight(1f))
                            Switch(
                                checked = step.onSeen == WatchAction.RESTART,
                                onCheckedChange = {
                                    step = step.copy(onSeen = if (it) WatchAction.RESTART else WatchAction.CONTINUE)
                                },
                            )
                        }
                        Text(
                            "Goes back to step 1 as soon as this is found, instead of carrying on to the next step. " +
                                "It doesn't use up a loop. Only used in Sequence mode.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Send a screenshot to Discord when found", Modifier.weight(1f))
                        Switch(checked = step.notify, onCheckedChange = { step = step.copy(notify = it) })
                    }
                    if (step.notify) {
                        Text(
                            "Needs a Discord webhook: tap the bell button on the home screen. At most one screenshot " +
                                "every few seconds for this step.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }

                    HorizontalDivider()
                    if (rule) {
                        Text("When it appears", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Checked in the background for the whole run, even while the steps are tapping. " +
                                "If two rules are on screen at once, the one higher in the Rules list goes first.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            WatchAction.entries.forEach { action ->
                                FilterChip(
                                    selected = step.onSeen == action,
                                    onClick = { step = step.copy(onSeen = action) },
                                    label = { Text(action.label) },
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Tap it when seen", Modifier.weight(1f))
                            Switch(checked = step.tapOnSeen, onCheckedChange = { step = step.copy(tapOnSeen = it) })
                        }
                        if (groups.isNotEmpty()) {
                            Text("Group (when one rule in a group is found, the whole group stops being checked):")
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = step.groupId == null,
                                    onClick = { step = step.copy(groupId = null) },
                                    label = { Text("No group") },
                                )
                                groups.forEach { g ->
                                    FilterChip(
                                        selected = step.groupId == g.id,
                                        onClick = { step = step.copy(groupId = g.id) },
                                        label = { Text(g.name) },
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }

                NumField(
                    if (isWatcher) "Wait after it appears, before the macro continues/restarts (ms)"
                    else "Wait afterwards (ms)",
                    step.delayAfterMs, Modifier.fillMaxWidth(),
                ) { step = step.copy(delayAfterMs = it.coerceAtLeast(0)) }

                // --- Advanced
                TextButton(onClick = { advanced = !advanced }) {
                    Text(if (advanced) "Hide advanced options" else "Advanced options")
                }
                if (advanced) {
                    if (tapsSomething) {
                        NumField(
                            if (kind == Kind.SWIPE) "Swipe time (ms)" else "Press time (ms)",
                            step.durationMs, Modifier.fillMaxWidth(),
                        ) { step = step.copy(durationMs = it.coerceAtLeast(1)) }
                    }
                    if (finds) {
                        OutlinedTextField(
                            value = threshold,
                            onValueChange = { threshold = it },
                            label = {
                                Text(
                                    if (kind == Kind.TEXT) "Match strictness (0.1 - 1.0, lower forgives misreads)"
                                    else "Match threshold (0.1 - 1.0)",
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (!rule) {
                            NumField("Look for up to (ms, sequence mode)", step.timeoutMs, Modifier.fillMaxWidth()) {
                                step = step.copy(timeoutMs = it.coerceAtLeast(0))
                            }
                        }
                    }
                    if (!isWatcher) {
                        NumField("Repeat", step.repeat.toLong(), Modifier.fillMaxWidth()) {
                            step = step.copy(repeat = it.toInt().coerceAtLeast(1))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(current()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
