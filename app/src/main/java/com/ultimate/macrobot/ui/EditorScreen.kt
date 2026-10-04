// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Ultrevo. See LICENSE and NOTICE.

package com.ultimate.macrobot.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.ultimate.macrobot.BuildConfig
import com.ultimate.macrobot.MacroBotApp
import com.ultimate.macrobot.data.RulePack
import com.ultimate.macrobot.data.RulePackException
import com.ultimate.macrobot.engine.MacroRunner
import com.ultimate.macrobot.engine.PendingTemplate
import com.ultimate.macrobot.model.Macro
import com.ultimate.macrobot.model.RunMode
import com.ultimate.macrobot.model.Step
import com.ultimate.macrobot.model.StepType
import com.ultimate.macrobot.service.MacroAccessibilityService
import com.ultimate.macrobot.service.ScreenCaptureService
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

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
    var editingRule by remember { mutableStateOf<Step?>(null) }
    var showGroups by remember { mutableStateOf(false) }
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

    fun upsertRule(rule: Step) = change { m ->
        if (m.rules.any { it.id == rule.id }) {
            m.copy(rules = m.rules.map { if (it.id == rule.id) rule else it })
        } else {
            m.copy(rules = m.rules + rule)
        }
    }

    fun moveRule(rule: Step, delta: Int) = change { m ->
        val list = m.orderedRules().toMutableList()
        val i = list.indexOfFirst { it.id == rule.id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return@change m
        val moved = list.removeAt(i)
        list.add(j, moved)
        m.copy(rules = list.mapIndexed { idx, r -> r.copy(priority = (idx + 1) * 10) })
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val current = repo.get(macroId)
        if (uri != null && current != null) {
            try {
                val count = context.contentResolver.openOutputStream(uri)?.use { out ->
                    RulePack.write(
                        current.orderedRules(),
                        current.groups,
                        current.name,
                        BuildConfig.VERSION_NAME,
                        { file -> runCatching { repo.templatePath(file).readBytes() }.getOrNull() },
                        out,
                    )
                } ?: throw RulePackException("Could not open the file for writing.")
                toast("Saved $count rule" + (if (count == 1) "" else "s") + ". Send that file to other players.")
            } catch (e: RulePackException) {
                toast(e.message ?: "Could not export the rules.")
            } catch (e: Exception) {
                toast("Could not save the file: ${e.message}")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val saved = mutableListOf<File>()
            try {
                val pack = context.contentResolver.openInputStream(uri)?.use { input ->
                    RulePack.read(input, ::cleanPng)
                } ?: throw RulePackException("Could not open the file.")
                val first = (repo.get(macroId)?.nextRulePriority() ?: 10)
                val rules = pack.rules.mapIndexed { i, item ->
                    var rule = item.rule.copy(priority = first + i * 10)
                    val bytes = item.png
                    if (bytes != null) {
                        val name = "${UUID.randomUUID()}.png"
                        val file = File(repo.templatesDir, name)
                        file.writeBytes(bytes)
                        saved.add(file)
                        rule = rule.copy(templateFile = name)
                    }
                    rule
                }
                change { m -> m.withImported(pack.groups, rules) }
                toast(
                    "Added ${rules.size} rule" + (if (rules.size == 1) "" else "s") +
                        " at the bottom. Check what each rule does (they tap things on your screen) and test them.",
                )
            } catch (e: RulePackException) {
                saved.forEach { it.delete() }
                toast(e.message ?: "Could not import the rules.")
            } catch (e: Exception) {
                saved.forEach { it.delete() }
                toast("Could not read the file: ${e.message}")
            }
        }
    }

    /** Saves the draft, then sends the user to their game to crop the picture (for a step or a rule). */
    fun startImagePick(draft: Step, save: (Step) -> Unit) {
        save(draft)
        editing = null
        editingRule = null
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
            if (tab == 1) {
                AddRuleButton(
                    onImage = { editingRule = Step(type = StepType.TAP_IMAGE, priority = macro.nextRulePriority(), watch = true) },
                    onText = { editingRule = Step(type = StepType.TAP_TEXT, priority = macro.nextRulePriority(), watch = true) },
                )
            }
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
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Rules (${macro.rules.size})") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Settings") })
            }
            if (tab == 1) {
                RulesTab(
                    macro = macro,
                    onToggle = { rule, on -> upsertRule(rule.copy(enabled = on)) },
                    onMove = ::moveRule,
                    onTest = { MacroRunner.testStep(it.testable()) },
                    onEdit = { editingRule = it },
                    onDelete = { rule -> change { m -> m.copy(rules = m.rules.filter { it.id != rule.id }) } },
                    onGroups = { showGroups = true },
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onExport = {
                        if (macro.rules.none { it.needsScreen }) {
                            toast("This macro has no rules to share yet.")
                        } else {
                            val safe = macro.name.filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }
                                .trim().ifEmpty { "rules" }
                            exportLauncher.launch(safe + RulePack.EXTENSION)
                        }
                    },
                )
            } else if (tab == 0) {
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
            onPickImage = { draft -> startImagePick(draft, ::upsert) },
        )
    }

    editingRule?.let { rule ->
        StepDialog(
            initial = rule,
            onDismiss = { editingRule = null },
            onSave = { upsertRule(it); editingRule = null },
            onPickImage = { draft -> startImagePick(draft, ::upsertRule) },
            rule = true,
            groups = macro.groups,
        )
    }

    if (showGroups) {
        GroupsDialog(macro = macro, onChange = ::change, onDismiss = { showGroups = false })
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
private fun AddRuleButton(onImage: () -> Unit, onText: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        ExtendedFloatingActionButton(
            onClick = { open = true },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Add rule") },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("When a picture appears") }, onClick = { open = false; onImage() })
            DropdownMenuItem(text = { Text("When text appears") }, onClick = { open = false; onText() })
        }
    }
}

@Composable
private fun RulesTab(
    macro: Macro,
    onToggle: (Step, Boolean) -> Unit,
    onMove: (Step, Int) -> Unit,
    onTest: (Step) -> Unit,
    onEdit: (Step) -> Unit,
    onDelete: (Step) -> Unit,
    onGroups: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    val ordered = macro.orderedRules()
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (ordered.isEmpty()) "No rules yet" else "Rules watch the screen the whole time",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "A rule looks for a picture or for words in the background while your macro runs, " +
                            "and reacts when it appears (for example a pop-up). If two rules are on screen at " +
                            "the same time, the one nearer the top is handled first. Use the menu to move a rule up or down.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = onGroups, modifier = Modifier.fillMaxWidth()) {
                        Text("Groups (" + macro.groups.size + ")")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Import rules") }
                        OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("Export rules") }
                    }
                }
            }
        }
        items(ordered, key = { it.id }) { rule ->
            StepRow(
                index = ordered.indexOf(rule) + 1,
                step = rule,
                detail = macro.groupName(rule.groupId).let { if (it.isEmpty()) "" else "[$it] " } + rule.ruleSummary(),
                onToggle = { onToggle(rule, it) },
                onUp = { onMove(rule, -1) },
                onDown = { onMove(rule, 1) },
                onTest = { onTest(rule) },
                onEdit = { onEdit(rule) },
                onDelete = { onDelete(rule) },
            )
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
                    "Runs top to bottom. Use the menu on a step to move it up or down. Tap a step to edit it.",
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
    detail: String = "${step.type.label} ${step.summary()}",
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
                    detail,
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
            NumField("Loop pause (ms)", macro.loopDelayMs, Modifier.weight(1f)) { v ->
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
                "(10000 = every 10 seconds). Reactive mode and rules keep checking until stopped.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/** Re-encodes a picture from a rule pack as a PNG, or returns null when it isn't a usable image. */
private fun cleanPng(bytes: ByteArray): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth < 4 || bounds.outHeight < 4 || bounds.outWidth > 4000 || bounds.outHeight > 4000) return null
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val out = ByteArrayOutputStream()
    val ok = bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    bitmap.recycle()
    return if (ok) out.toByteArray() else null
}
