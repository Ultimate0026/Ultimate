package com.ultimate.macrobot.ui

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ultimate.macrobot.MacroBotApp
import com.ultimate.macrobot.model.Macro
import com.ultimate.macrobot.service.MacroAccessibilityService
import com.ultimate.macrobot.service.ScreenCaptureService
import kotlinx.coroutines.delay

@Composable
fun AppRoot(onGrantCapture: () -> Unit, onSendToBackground: () -> Unit) {
    val repo = MacroBotApp.repo
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = editingId != null) { editingId = null }

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
    Scaffold(
        topBar = { TopAppBar(title = { Text("MacroBot") }) },
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
                        IconButton(onClick = { repo.delete(m.id) }) {
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
            Text("Setup", style = MaterialTheme.typography.titleMedium)

            Text("1. Accessibility service: ${if (accessibilityOn) "ON" else "OFF"}")
            if (!accessibilityOn) {
                Text(
                    "Turn on MacroBot under Downloaded/Installed apps. If Android says \"restricted " +
                        "setting\", open Settings > Apps > MacroBot > menu > Allow restricted settings first.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("Open accessibility settings") }
            }

            Text("2. Screen capture (image recognition): ${if (captureOn) "ON" else "OFF"}")
            if (captureOn) {
                OutlinedButton(onClick = { ScreenCaptureService.stop(context) }) { Text("Turn off") }
            } else {
                OutlinedButton(onClick = onGrantCapture) { Text("Grant screen capture") }
            }

            Text("3. Floating controls (RUN / REC / CROP over your game)")
            Button(onClick = {
                val svc = MacroAccessibilityService.instance
                if (svc == null) {
                    Toast.makeText(context, "Enable the accessibility service first", Toast.LENGTH_SHORT).show()
                } else {
                    svc.overlay.showBubble()
                }
            }) { Text("Show floating controls") }
        }
    }
}
