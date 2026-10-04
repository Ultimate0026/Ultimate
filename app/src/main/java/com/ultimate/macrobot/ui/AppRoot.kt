package com.ultimate.macrobot.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ultimate.macrobot.BuildConfig
import com.ultimate.macrobot.MacroBotApp
import com.ultimate.macrobot.data.ApkInstaller
import com.ultimate.macrobot.data.UpdateChecker
import com.ultimate.macrobot.model.Macro
import com.ultimate.macrobot.service.MacroAccessibilityService
import com.ultimate.macrobot.service.ScreenCaptureService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppRoot(
    onGrantCapture: () -> Unit,
    onSendToBackground: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val repo = MacroBotApp.repo
    val context = LocalContext.current
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = editingId != null) { editingId = null }

    var update by remember { mutableStateOf<UpdateChecker.Update?>(null) }
    LaunchedEffect(Unit) {
        if (repo.updateChecksEnabled) {
            update = withContext(Dispatchers.IO) { UpdateChecker.check(BuildConfig.VERSION_NAME) }
        }
    }
    var progress by remember { mutableStateOf<Float?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    update?.let { u ->
        val downloading = progress != null
        AlertDialog(
            onDismissRequest = { if (!downloading) update = null },
            title = { Text("Update available") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("MacroBot ${u.version} is out (you have ${BuildConfig.VERSION_NAME}).")
                    if (u.apkUrl != null) {
                        Text(
                            "Update now downloads it and installs it over this app, keeping your macros. " +
                                "Android will ask you to confirm.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    progress?.let { p -> LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth()) }
                    updateError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !downloading, onClick = {
                    if (u.apkUrl == null) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.url)))
                        update = null
                    } else if (!context.packageManager.canRequestPackageInstalls()) {
                        updateError = "First allow MacroBot to install updates on the page that just opened, " +
                            "then come back and tap Update now again."
                        context.startActivity(
                            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")),
                        )
                    } else {
                        updateError = null
                        progress = 0f
                        scope.launch {
                            val error = ApkInstaller.downloadAndInstall(context, u) { progress = it }
                            progress = null
                            if (error != null) updateError = error else update = null
                        }
                    }
                }) { Text(if (u.apkUrl == null) "Download" else "Update now") }
            },
            dismissButton = {
                Row {
                    if (u.apkUrl != null) {
                        TextButton(enabled = !downloading, onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.url)))
                        }) { Text("Release page") }
                    }
                    TextButton(enabled = !downloading, onClick = { update = null }) { Text("Later") }
                }
            },
        )
    }

    val id = editingId
    if (id == null) {
        HomeScreen(
            onOpen = { repo.setActive(it); editingId = it },
            onGrantCapture = onGrantCapture,
            onRequestNotifications = onRequestNotifications,
        )
    } else {
        EditorScreen(
            macroId = id,
            onBack = { editingId = null },
            onGrantCapture = onGrantCapture,
            onSendToBackground = onSendToBackground,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    onOpen: (String) -> Unit,
    onGrantCapture: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val repo = MacroBotApp.repo
    val macros by repo.macros.collectAsState()
    var pendingDelete by remember { mutableStateOf<Macro?>(null) }
    var showAbout by remember { mutableStateOf(false) }
    if (showAbout) AboutDialog(onDismiss = { showAbout = false })
    pendingDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete \"${m.name}\"?") },
            text = { Text("The macro and its saved images will be removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { repo.delete(m.id); pendingDelete = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("MacroBot") },
                actions = {
                    IconButton(onClick = { showAbout = true }) {
                        Icon(Icons.Default.Info, contentDescription = "About and privacy")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val m = Macro(name = "Macro ${macros.size + 1}")
                    repo.add(m)
                    onOpen(m.id)
                },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New macro") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SetupSection(onGrantCapture, onRequestNotifications) }
            if (macros.isEmpty()) {
                item { Text("No macros yet. Create one, then record inputs or add steps by hand.") }
            }
            items(macros, key = { it.id }) { m ->
                Card(Modifier.fillMaxWidth().clickable { onOpen(m.id) }) {
                    Row(
                        Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(m.name, style = MaterialTheme.typography.titleMedium)
                            Text("${m.steps.size} steps - ${m.mode.label}")
                        }
                        IconButton(onClick = { pendingDelete = m }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete macro")
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("About MacroBot ${BuildConfig.VERSION_NAME}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Records and replays taps and swipes, and can react to images and text on screen. " +
                        "Your macros and screenshots never leave your device.",
                )
                Text(
                    "The app checks GitHub for newer releases (optional), and the text-recognition " +
                        "library (Google ML Kit) may send anonymous usage statistics - never your screen content.",
                    style = MaterialTheme.typography.bodySmall,
                )
                var checks by remember { mutableStateOf(MacroBotApp.repo.updateChecksEnabled) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Check for updates on launch", Modifier.weight(1f))
                    Switch(checked = checks, onCheckedChange = {
                        checks = it
                        MacroBotApp.repo.updateChecksEnabled = it
                    })
                }
                Text(
                    "Responsible use: many games forbid automation in their terms of service and may " +
                        "suspend accounts that use it. You are responsible for how you use this app.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
