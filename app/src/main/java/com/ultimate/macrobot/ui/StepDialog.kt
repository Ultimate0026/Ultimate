package com.ultimate.macrobot.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ultimate.macrobot.model.Step
import com.ultimate.macrobot.model.StepType

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
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StepType.entries.forEach { type ->
                        FilterChip(
                            selected = step.type == type,
                            onClick = { step = step.copy(type = type) },
                            label = { Text(type.label) },
                        )
                    }
                }

                if (!step.needsImage) {
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
                if (step.type == StepType.TAP || step.type == StepType.SWIPE || step.type == StepType.TAP_IMAGE) {
                    NumField(
                        if (step.type == StepType.SWIPE) "Swipe time (ms)" else "Press time (ms)",
                        step.durationMs, Modifier.fillMaxWidth(),
                    ) { step = step.copy(durationMs = it.coerceAtLeast(1)) }
                }

                if (step.needsImage) {
                    OutlinedButton(onClick = { onPickImage(current()) }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (step.templateFile == null) "Pick image from screen" else "Re-pick image from screen")
                    }
                    OutlinedTextField(
                        value = threshold,
                        onValueChange = { threshold = it },
                        label = { Text("Match threshold (0.1 - 1.0)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    NumField("Look for up to (ms, sequence mode)", step.timeoutMs, Modifier.fillMaxWidth()) {
                        step = step.copy(timeoutMs = it.coerceAtLeast(0))
                    }
                }

                NumField("Delay after (ms)", step.delayAfterMs, Modifier.fillMaxWidth()) {
                    step = step.copy(delayAfterMs = it.coerceAtLeast(0))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumField("Priority (low runs first)", step.priority.toLong(), Modifier.weight(1f)) {
                        step = step.copy(priority = it.toInt())
                    }
                    NumField("Repeat", step.repeat.toLong(), Modifier.weight(1f)) {
                        step = step.copy(repeat = it.toInt().coerceAtLeast(1))
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
