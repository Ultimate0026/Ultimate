// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Ultrevo. See LICENSE and NOTICE.

package com.ultimate.macrobot.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ultimate.macrobot.MacroBotApp
import com.ultimate.macrobot.data.DiscordWebhook
import com.ultimate.macrobot.engine.DiscordNotifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where to paste the Discord webhook that "send a screenshot to Discord" steps and rules post to. */
@Composable
fun DiscordDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(MacroBotApp.repo.webhookUrl) }
    var shown by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Discord alerts") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Steps and rules with \"Send a screenshot to Discord when found\" switched on post a screenshot " +
                        "of your screen to this webhook when they are found.",
                )
                Text(
                    "In Discord: channel settings > Integrations > Webhooks > New Webhook > Copy Webhook URL, " +
                        "then paste it here.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Discord webhook URL") },
                    placeholder = { Text("https://discord.com/api/webhooks/...") },
                    singleLine = true,
                    visualTransformation = if (shown) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { shown = !shown }) { Text(if (shown) "Hide" else "Show") }
                    OutlinedButton(
                        enabled = !testing,
                        onClick = {
                            if (!DiscordWebhook.isValid(url)) {
                                toast("That doesn't look like a Discord webhook address. It starts with https://discord.com/api/webhooks/")
                            } else {
                                testing = true
                                scope.launch {
                                    val problem = withContext(Dispatchers.IO) { DiscordNotifier.sendTest(url.trim()) }
                                    testing = false
                                    toast(problem ?: "Sent. Check your Discord channel for the message.")
                                }
                            }
                        },
                    ) { Text(if (testing) "Sending..." else "Send test") }
                }
                Text(
                    "Keep this address private: anyone who has it can post in that channel. It is saved only on this " +
                        "phone and is never included in a shared rules file. The screenshot shows your whole screen " +
                        "at that moment. At most one screenshot is sent every few seconds for each step. " +
                        "Leave it empty to turn this off.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clean = url.trim()
                if (clean.isNotEmpty() && !DiscordWebhook.isValid(clean)) {
                    toast("The Discord webhook doesn't look right. It should start with https://discord.com/api/webhooks/ (or leave it empty).")
                } else {
                    MacroBotApp.repo.webhookUrl = clean
                    onDismiss()
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
