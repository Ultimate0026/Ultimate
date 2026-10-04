package com.ultimate.macrobot.ui

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ultimate.macrobot.MacroBotApp
import com.ultimate.macrobot.engine.MacroRunner
import com.ultimate.macrobot.engine.PendingTemplate
import com.ultimate.macrobot.model.Macro
import com.ultimate.macrobot.model.RunMode
import com.ultimate.macrobot.model.Step
import com.ultimate.macrobot.model.StepType
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
    var tab by rememberSaveable { mutableStateOf(0) }

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

    /** Shows the floating RUN / REC / CROP bar and returns to the game. */
    fun openFloatingControls(message: String? = null) {
        val svc = MacroAccessibilityService.instance
        if (svc == null) {
            toast("Enable the accessibility service first (Home screen)")
        } else {
            svc.overlay.showBubble()
            if (message != null) toast(message)
            onSendToBackground()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(macro.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            RunBar(
                running = running,
                status = status,
                onToggle = {
                    if (running) {
                        MacroRunner.stop()
                    } else {
                        MacroAccessibilityService.instance?.overlay?.showBubble()
                        val error = MacroRunner.start(context)
                        if (error != null) toast(error) else onSendToBackground()
                    }
                },
                onControls = { openFloatingControls() },
            )
        },
        floatingActionButton = {
            if (tab == 0) {
                AddStepButton(
                    onRecord = {
                        openFloatingControls("Press REC, play your inputs in the game, then press DONE")
                    },
                    onTapSwipe = { editing = Step(priority = macro.nextPriority()) },
                    onImage = { editing = Step(type = StepType.TAP_IMAGE, priority = macro.nextPriority()) },
                    onText = { editing = Step(type = StepType.TAP_TEXT, priority = macro.nextPriority()) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Steps (${macro.steps.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Settings") })
            }
            if (tab == 0) {
                StepsTab(
                    macro = macro,
                    onToggle = { step, on -> upsert(step.copy(enabled = on)) },
                    onMove = ::move,
                    onTest = { MacroRunner.testStep(it) },
                    onEdit = { editing = it },
                    onDelete = { step -> change { m -> m.copy(steps = m.steps.filter { it.id != step.id }) } },
                )
            } else {
                SettingsTab(macro = macro, onChange = ::change)
            }
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
                        toast("Grant screen capture first")
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
private fun RunBar(running: Boolean, status: String, onToggle: () -> Unit, onControls: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (running) Text("Running: $status", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onToggle, modifier = Modifier.weight(1f)) {
                    Text(if (running) "Stop" else "Start")
                }
                OutlinedButton(onClick = onControls, modifier = Modifier.weight(1f)) {
                    Text("Floating controls")
                }
            }
        }
    }
}

@Composable
private fun AddStepButton(
    onRecord: () -> Unit,
    onTapSwipe: () -> Unit,
    onImage: () -> Unit,
    onText: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        ExtendedFloatingActionButton(
            onClick = { open = true },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Add step") },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Record inputs in the game") }, onClick = { open = false; onRecord() })
            DropdownMenuItem(text = { Text("Tap or swipe (type position)") }, onClick = { open = false; onTapSwipe() })
            DropdownMenuItem(text = { Text("Find a picture on screen") }, onClick = { open = false; onImage() })
            DropdownMenuItem(text = { Text("Find text on screen") }, onClick = { open = false; onText() })
        }
    }
}

@Composable
private fun StepsTab(
    macro: Macro,
    onToggle: (Step, Boolean) -> Unit,
    onMove: (Step, Int) -> Unit,
    onTest: (Step) -> Unit,
    onEdit: (Step) -> Unit,
    onDelete: (Step) -> Unit,
) {
    val ordered = macro.ordered()
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (ordered.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("No steps yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap Add step. You can record your inputs in the game, or add taps, " +
                                "pictures and text by hand.",
                        )
                    }
                }
            }
        } else {
            item {
                Text(
                    "Runs top to bottom. Tap a step to edit it.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        items(ordered, key = { it.id }) { step ->
            StepRow(
                index = ordered.indexOf(step) + 1,
                step = step,
                onToggle = { onToggle(step, it) },
                onUp = { onMove(step, -1) },
                onDown = { onMove(step, 1) },
                onTest = { onTest(step) },
                onEdit = { onEdit(step) },
                onDelete = { onDelete(step) },
            )
        }
    }
}

@Composable
private fun StepRow(
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
    var menu by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().clickable(onClick = onEdit)) {
        Row(
            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "$index. ${step.title()}",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${step.type.label} ${step.summary()}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "wait ${step.delayAfterMs} ms" + if (step.repeat > 1) " - x${step.repeat}" else "",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (thumb != null) {
                Image(thumb, contentDescription = "Template", modifier = Modifier.size(40.dp).padding(end = 8.dp))
            }
            Switch(checked = step.enabled, onCheckedChange = onToggle)
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More actions") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; onEdit() })
                    DropdownMenuItem(text = { Text("Test this step") }, onClick = { menu = false; onTest() })
                    DropdownMenuItem(text = { Text("Move up") }, onClick = { menu = false; onUp() })
                    DropdownMenuItem(text = { Text("Move down") }, onClick = { menu = false; onDown() })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                }
            }
        }
    }
}

@Composable
private fun SettingsTab(macro: Macro, onChange: ((Macro) -> Macro) -> Unit) {
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        var name by remember(macro.id) { mutableStateOf(macro.name) }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; onChange { m -> m.copy(name = it) } },
            label = { Text("Macro name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Spacer(Modifier.height(6.dp))
        Text("How it runs", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RunMode.entries.forEach { mode ->
                FilterChip(
                    selected = macro.mode == mode,
                    onClick = { onChange { it.copy(mode = mode) } },
                    label = { Text(mode.label) },
                )
            }
        }
        Text(macro.mode.help, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumField("Loops (0 = forever)", macro.loops.toLong(), Modifier.weight(1f)) { v ->
                onChange { it.copy(loops = v.toInt().coerceAtLeast(0)) }
            }
            NumField("Pause between loops (ms)", macro.loopDelayMs, Modifier.weight(1f)) { v ->
                onChange { it.copy(loopDelayMs = v.coerceAtLeast(0)) }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text("Screen checks", style = MaterialTheme.typography.titleSmall)
        NumField("Look at the screen every (ms)", macro.scanIntervalMs, Modifier.fillMaxWidth()) { v ->
            onChange { it.copy(scanIntervalMs = v.coerceAtLeast(100)) }
        }
        Text(
            "How often image and text steps check the screen. Higher is easier on the battery " +
                "(10000 = every 10 seconds). Reactive mode and always-watching steps keep checking until stopped.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
