package com.ultimate.macrobot.ui

import androidx.compose.foundation.horizontalScroll
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StepDialog(
    initial: Step,
    onDismiss: () -> Unit,
    onSave: (Step) -> Unit,
    onPickImage: (Step) -> Unit,
) {
    var step by remember { mutableStateOf(initial) }
    var threshold by remember { mutableStateOf(initial.threshold.toString()) }

    fun current(): Step = step.copy(threshold = threshold.toFloatOrNull()?.coerceIn(0.1f, 1f) ?: step.threshold)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit step") },
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepType.entries.forEach { type ->
                        FilterChip(
                            selected = step.type == type,
                            onClick = { step = step.copy(type = type) },
                            label = { Text(type.label) },
                        )
                    }
                }
                Text(
                    when (step.type) {
                        StepType.TAP -> "Taps one fixed spot on the screen."
                        StepType.SWIPE -> "Drags from one spot to another."
                        StepType.WAIT_IMAGE -> "Waits until a picture you choose is on screen. Does not tap."
                        StepType.TAP_IMAGE -> "Looks for a picture you choose and taps it wherever it appears."
                        StepType.WAIT_TEXT -> "Waits until the words you type appear on screen. Does not tap."
                        StepType.TAP_TEXT -> "Looks for the words you type and taps them wherever they appear."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )

                if (!step.needsScreen) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("X", step.x.toLong(), Modifier.weight(1f)) { step = step.copy(x = it.toInt()) }
                        NumField("Y", step.y.toLong(), Modifier.weight(1f)) { step = step.copy(y = it.toInt()) }
                    }
                }
                if (step.type == StepType.SWIPE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("End X", step.x2.toLong(), Modifier.weight(1f)) { step = step.copy(x2 = it.toInt()) }
                        NumField("End Y", step.y2.toLong(), Modifier.weight(1f)) { step = step.copy(y2 = it.toInt()) }
                    }
                }
                if (step.type == StepType.TAP || step.type == StepType.SWIPE || step.tapsTarget) {
                    NumField(
                        if (step.type == StepType.SWIPE) "Swipe time (ms)" else "Press time (ms)",
                        step.durationMs, Modifier.fillMaxWidth(),
                    ) { step = step.copy(durationMs = it.coerceAtLeast(1)) }
                }

                if (step.needsScreen) {
                    if (step.isImage) {
                        Text(
                            "How to pick the picture:\n" +
                                "1. Press the button below (MacroBot goes to the background).\n" +
                                "2. Open your game and press CROP on the floating bar.\n" +
                                "3. Drag a box around the button or icon to look for.\n" +
                                "4. Come back here - a thumbnail shows on the step.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(onClick = { onPickImage(current()) }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (step.templateFile == null) "Pick image from screen" else "Re-pick image from screen")
                        }
                    } else {
                        OutlinedTextField(
                            value = step.text,
                            onValueChange = { step = step.copy(text = it) },
                            label = { Text("Text to look for") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            "Capital letters, spaces and punctuation are ignored. Works for Latin letters " +
                                "and numbers (English and similar). Pick a distinctive word or phrase.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedTextField(
                        value = threshold,
                        onValueChange = { threshold = it },
                        label = {
                            Text(
                                if (step.isText) "Match strictness (0.1 - 1.0, lower forgives misreads)"
                                else "Match threshold (0.1 - 1.0)",
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Always watching")
                            Text(
                                "Checks the screen in the background for the whole run, even while " +
                                    "your other steps are tapping. Use it for pop-ups like \"I'm here\".",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(checked = step.watch, onCheckedChange = { step = step.copy(watch = it) })
                    }
                    if (step.watch) {
                        Text("When it appears:")
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
                            Text(if (step.isText) "Tap the text when seen" else "Tap the image when seen", Modifier.weight(1f))
                            Switch(checked = step.tapOnSeen, onCheckedChange = { step = step.copy(tapOnSeen = it) })
                        }
                    } else {
                        NumField("Look for up to (ms, sequence mode)", step.timeoutMs, Modifier.fillMaxWidth()) {
                            step = step.copy(timeoutMs = it.coerceAtLeast(0))
                        }
                    }
                }

                val isWatcher = step.needsScreen && step.watch
                NumField(
                    if (isWatcher) "Wait after it appears, before the macro continues/restarts (ms)" else "Delay after (ms)",
                    step.delayAfterMs, Modifier.fillMaxWidth(),
                ) {
                    step = step.copy(delayAfterMs = it.coerceAtLeast(0))
                }
                if (!isWatcher) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumField("Priority (low runs first)", step.priority.toLong(), Modifier.weight(1f)) {
                            step = step.copy(priority = it.toInt())
                        }
                        NumField("Repeat", step.repeat.toLong(), Modifier.weight(1f)) {
                            step = step.copy(repeat = it.toInt().coerceAtLeast(1))
                        }
                    }
                }
                Text(
                    "Priority decides order: 10 runs before 20. Equal numbers keep list order.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(current()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
