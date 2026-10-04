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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ultimate.macrobot.BuildConfig
import com.ultimate.macrobot.MacroBotApp
import com.ultimate.macrobot.data.UpdateChecker
import com.ultimate.macrobot.model.Macro
import com.ultimate.macrobot.service.MacroAccessibilityService
import com.ultimate.macrobot.service.ScreenCaptureService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun AppRoot(onGrantCapture: () -> Unit, onSendToBackground: () -> Unit) {
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
    update?.let { u ->
        AlertDialog(
            onDismissRequest = { update = null },
            title = { Text("Update available") },
            text = { Text("MacroBot ${u.version} is out (you have ${BuildConfig.VERSION_NAME}). Download opens the release page; tap the .apk there to install it over the current version.") },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u.url)))
                    update = null
                }) { Text("Download") }
            },
            dismissButton = { TextButton(onClick = { update = null }) { Text("Later") } },
        )
    }

    val id = editingId
    if (id == null) {
        HomeScreen(
            onOpen = { repo.setActive(it); editingId = it },
            onGrantCapture = onGrantCapture,
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
private fun HomeScreen(onOpen: (String) -> Unit, onGrantCapture: () -> Unit) {
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
            item { SetupCard(onGrantCapture) }
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
fun SetupCard(onGrantCapture: () -> Unit) {
    val context = LocalContext.current
    // Service state changes outside Compose, so poll it.
    val tick by produceState(0) { while (true) { delay(1000); value++ } }
    val accessibilityOn = tick >= 0 && MacroAccessibilityService.instance != null
    val captureOn = tick >= 0 && ScreenCaptureService.instance?.isReady == true

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (accessibilityOn) "Ready" else "Setup needed",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Accessibility ${if (accessibilityOn) "on" else "off"} - " +
                    "Screen capture ${if (captureOn) "on" else "off"}",
                style = MaterialTheme.typography.bodySmall,
            )
            if (!accessibilityOn) {
                Text(
                    "Turn on MacroBot under Accessibility > Downloaded/Installed apps. If Android says " +
                        "\"restricted setting\", open Settings > Apps > MacroBot > menu > " +
                        "Allow restricted settings first.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("Open accessibility settings") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val svc = MacroAccessibilityService.instance
                    if (svc == null) {
                        Toast.makeText(context, "Enable the accessibility service first", Toast.LENGTH_SHORT).show()
                    } else {
                        svc.overlay.showBubble()
                    }
                }) { Text("Floating controls") }
                if (captureOn) {
                    OutlinedButton(onClick = { ScreenCaptureService.stop(context) }) { Text("Stop capture") }
                } else {
                    OutlinedButton(onClick = onGrantCapture) { Text("Grant screen capture") }
                }
            }
            Text(
                "Screen capture is only needed for image and text steps.",
                style = MaterialTheme.typography.bodySmall,
            )
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
