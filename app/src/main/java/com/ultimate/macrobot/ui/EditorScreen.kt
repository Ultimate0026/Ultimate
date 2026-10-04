package com.ultimate.macrobot.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ultimate.macrobot.MacroBotApp
import com.ultimate.macrobot.engine.MacroRunner
import com.ultimate.macrobot.engine.PendingTemplate
import com.ultimate.macrobot.model.Macro
import com.ultimate.macrobot.model.RunMode
import com.ultimate.macrobot.model.Step
import com.ultimate.macrobot.service.MacroAccessibilityService
import com.ultimate.macrobot.service.ScreenCaptureService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    macroId: String,
    onBack: () -> Unit,
    onGrantCapture: () -> Unit,
    onSendToBackground: () -> Unit,
) {
    val context = LocalContext.current
    val repo = MacroBotApp.repo
    val macros by repo.macros.collectAsState()
    val macro = macros.firstOrNull { it.id == macroId }
    if (macro == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val running by MacroRunner.running.collectAsState()
    val status by MacroRunner.status.collectAsState()
    var editing by remember { mutableStateOf<Step?>(null) }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
    fun change(transform: (Macro) -> Macro) = repo.update(macroId, transform)

    fun upsert(step: Step) = change { m ->
        if (m.steps.any { it.id == step.id }) {
            m.copy(steps = m.steps.map { if (it.id == step.id) step else it })
        } else {
            m.copy(steps = m.steps + step)
        }
    }

    fun move(step: Step, delta: Int) = change { m ->
        val list = m.ordered().toMutableList()
        val i = list.indexOfFirst { it.id == step.id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return@change m
        val moved = list.removeAt(i)
        list.add(j, moved)
        m.copy(steps = list.mapIndexed { idx, s -> s.copy(priority = (idx + 1) * 10) })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(macro.name) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        var name by remember(macroId) { mutableStateOf(macro.name) }
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it; change { m -> m.copy(name = it) } },
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            RunMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = macro.mode == mode,
                                    onClick = { change { it.copy(mode = mode) } },
                                    label = { Text(mode.label) },
                                )
                            }
                        }
                        Text(macro.mode.help, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NumField(
                                "Loops (0 = forever)", macro.loops.toLong(), Modifier.weight(1f),
                            ) { v -> change { it.copy(loops = v.toInt().coerceAtLeast(0)) } }
                            NumField(
                                "Delay between loops (ms)", macro.loopDelayMs, Modifier.weight(1f),
                            ) { v -> change { it.copy(loopDelayMs = v.coerceAtLeast(0)) } }
                        }
                        NumField(
                            "Check screen for images every (ms)", macro.scanIntervalMs, Modifier.fillMaxWidth(),
                        ) { v -> change { it.copy(scanIntervalMs = v.coerceAtLeast(100)) } }
                        Text(
                            "Image steps look at the screen this often. Higher = easier on battery " +
                                "(10000 = every 10 seconds). Reactive mode keeps watching until stopped.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = {
                                if (running) {
                                    MacroRunner.stop()
                                } else {
                                    MacroAccessibilityService.instance?.overlay?.showBubble()
                                    val error = MacroRunner.start(context)
                                    if (error != null) toast(error) else onSendToBackground()
                                }
                            }) { Text(if (running) "Stop" else "Start") }
                            OutlinedButton(onClick = {
                                val svc = MacroAccessibilityService.instance
                                if (svc == null) {
                                    toast("Enable the accessibility service first (Home screen)")
                                } else {
                                    svc.overlay.showBubble()
                                    onSendToBackground()
                                }
                            }) { Text("Floating controls") }
                        }
                        if (running) Text("Running: $status")
                        Text(
                            "To record: tap Floating controls, open your game, press REC, play " +
                                "your inputs, press DONE. They appear below as steps you can edit.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Steps (${macro.steps.size}) - runs top to bottom",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { editing = Step(priority = macro.nextPriority()) }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("Add step")
                    }
                }
            }

            val ordered = macro.ordered()
            items(ordered, key = { it.id }) { step ->
                StepCard(
                    index = ordered.indexOf(step) + 1,
                    step = step,
                    onToggle = { upsert(step.copy(enabled = it)) },
                    onUp = { move(step, -1) },
                    onDown = { move(step, 1) },
                    onTest = { MacroRunner.testStep(step) },
                    onEdit = { editing = step },
                    onDelete = { change { m -> m.copy(steps = m.steps.filter { it.id != step.id }) } },
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    editing?.let { step ->
        StepDialog(
            initial = step,
            onDismiss = { editing = null },
            onSave = { upsert(it); editing = null },
            onPickImage = { draft ->
                upsert(draft)
                editing = null
                val svc = MacroAccessibilityService.instance
                when {
                    svc == null -> toast("Enable the accessibility service first (Home screen)")
                    ScreenCaptureService.instance?.isReady != true -> {
                        toast("Grant screen capture first (Home screen)")
                        onGrantCapture()
                    }
                    else -> {
                        MacroRunner.setPendingTemplate(PendingTemplate(macroId, draft.id))
                        svc.overlay.showBubble()
                        toast("Open your game, press CROP, then drag a box around the target")
                        onSendToBackground()
                    }
                }
            },
        )
    }
}

@Composable
private fun StepCard(
    index: Int,
    step: Step,
    onToggle: (Boolean) -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onTest: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val repo = MacroBotApp.repo
    val thumb = remember(step.templateFile) {
        step.templateFile?.let { BitmapFactory.decodeFile(repo.templatePath(it).path)?.asImageBitmap() }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$index. ${step.title()}", style = MaterialTheme.typography.titleMedium)
                    Text("${step.type.label} ${step.summary()}")
                    Text(
                        "priority ${step.priority} - wait ${step.delayAfterMs} ms" +
                            if (step.repeat > 1) " - x${step.repeat}" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (thumb != null) {
                    Image(thumb, contentDescription = "Template", modifier = Modifier.size(48.dp).padding(end = 8.dp))
                }
                Switch(checked = step.enabled, onCheckedChange = onToggle)
            }
            Row {
                IconButton(onClick = onUp) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                IconButton(onClick = onDown) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                IconButton(onClick = onTest) { Icon(Icons.Default.PlayArrow, "Test step") }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit step") }
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete step") }
            }
        }
    }
}
