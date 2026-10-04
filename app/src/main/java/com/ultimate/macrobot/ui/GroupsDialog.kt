// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Ultrevo. See LICENSE and NOTICE.

package com.ultimate.macrobot.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ultimate.macrobot.model.Macro
import com.ultimate.macrobot.model.RuleGroup

/** Manage a macro's rule groups: when one rule in a group is found, the whole group stops being checked. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupsDialog(macro: Macro, onChange: ((Macro) -> Macro) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selectedId by remember { mutableStateOf(macro.groups.firstOrNull()?.id) }
    // null = no name box open, "" = naming a new group, otherwise the id of the group being renamed
    var naming by remember { mutableStateOf<String?>(null) }
    var nameText by remember { mutableStateOf("") }
    val selected = macro.groups.firstOrNull { it.id == selectedId } ?: macro.groups.firstOrNull()

    fun nameTaken(name: String, ignoreId: String?): Boolean =
        macro.groups.any { it.name.equals(name, ignoreCase = true) && it.id != ignoreId }

    fun update(groupId: String, change: (RuleGroup) -> RuleGroup) = onChange { m ->
        m.copy(groups = m.groups.map { if (it.id == groupId) change(it) else it })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rule groups") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Put rules in a group when only one of them needs to be found, for example three pictures of the same " +
                        "pop-up. As soon as one rule in the group is found, the whole group stops being checked. Choose a " +
                        "group for a rule in the rule's own window.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (macro.groups.isEmpty()) Text("No groups yet.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    macro.groups.forEach { g ->
                        val count = macro.rules.count { it.groupId == g.id }
                        FilterChip(
                            selected = g.id == selected?.id,
                            onClick = { selectedId = g.id },
                            label = { Text("${g.name} ($count)") },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { nameText = ""; naming = "" }) { Text("New") }
                    if (selected != null) {
                        OutlinedButton(onClick = { nameText = selected.name; naming = selected.id }) { Text("Rename") }
                        OutlinedButton(onClick = {
                            val id = selected.id
                            selectedId = null
                            onChange { m -> m.withoutGroup(id) }
                        }) { Text("Delete") }
                    }
                }
                if (selected != null) {
                    NumField(
                        "Stop checking for (seconds, 0 = until the macro stops)",
                        selected.pauseS.toLong(),
                        Modifier.fillMaxWidth(),
                    ) { v -> update(selected.id) { it.copy(pauseS = v.toInt().coerceIn(0, 86400)) } }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Start checking again when the macro restarts", Modifier.weight(1f))
                        Switch(
                            checked = selected.resetOnRestart,
                            onCheckedChange = { on -> update(selected.id) { it.copy(resetOnRestart = on) } },
                        )
                    }
                    Text(
                        "The macro restarts when it finishes a loop and starts over (Sequence mode), or when a rule set to " +
                            "restart the macro does it. Use seconds for a pop-up that comes back now and then.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )

    val renaming = naming
    if (renaming != null) {
        AlertDialog(
            onDismissRequest = { naming = null },
            title = { Text(if (renaming.isEmpty()) "New group" else "Rename group") },
            text = {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    label = { Text("Group name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = nameText.trim().take(60)
                    when {
                        name.isEmpty() -> naming = null
                        nameTaken(name, renaming.ifEmpty { null }) ->
                            Toast.makeText(context, "There is already a group with that name.", Toast.LENGTH_SHORT).show()
                        renaming.isEmpty() -> {
                            val group = RuleGroup(name = name)
                            onChange { m -> m.copy(groups = m.groups + group) }
                            selectedId = group.id
                            naming = null
                        }
                        else -> {
                            update(renaming) { it.copy(name = name) }
                            naming = null
                        }
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { naming = null }) { Text("Cancel") } },
        )
    }
}
