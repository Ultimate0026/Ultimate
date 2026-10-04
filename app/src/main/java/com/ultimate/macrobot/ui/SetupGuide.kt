package com.ultimate.macrobot.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ultimate.macrobot.service.MacroAccessibilityService
import com.ultimate.macrobot.service.ScreenCaptureService
import kotlinx.coroutines.delay

/** What is switched on right now. Refreshed every second because Android settings change outside the app. */
data class SetupState(val accessibility: Boolean, val notifications: Boolean, val capture: Boolean) {
    /** The permissions Ultrebo needs to run at all; screen capture is only for image and text steps. */
    val requiredDone: Boolean get() = accessibility && notifications
}

private val needsNotificationPermission: Boolean get() = Build.VERSION.SDK_INT >= 33

private fun notificationsAllowed(context: Context): Boolean =
    !needsNotificationPermission ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

@Composable
private fun rememberSetupState(): SetupState {
    val context = LocalContext.current
    val tick by produceState(0) { while (true) { delay(1000); value++ } }
    return remember(tick) {
        SetupState(
            accessibility = MacroAccessibilityService.instance != null,
            notifications = notificationsAllowed(context),
            capture = ScreenCaptureService.instance?.isReady == true,
        )
    }
}

/** A step-by-step guide until the required permissions are on, then a short status card. */
@Composable
fun SetupSection(onGrantCapture: () -> Unit, onRequestNotifications: () -> Unit) {
    val state = rememberSetupState()
    if (state.requiredDone) {
        ReadyCard(state, onGrantCapture)
    } else {
        TutorialCard(state, onGrantCapture, onRequestNotifications)
    }
}

@Composable
private fun TutorialCard(state: SetupState, onGrantCapture: () -> Unit, onRequestNotifications: () -> Unit) {
    val context = LocalContext.current
    val total = if (needsNotificationPermission) 2 else 1
    val done = (if (state.accessibility) 1 else 0) +
        (if (needsNotificationPermission && state.notifications) 1 else 0)
    val current = when {
        !state.accessibility -> 1
        !state.notifications -> 2
        else -> 0
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Get started", style = MaterialTheme.typography.titleLarge)
            Text(
                "$done of $total required steps done. This guide goes away by itself once they are all on.",
                style = MaterialTheme.typography.bodySmall,
            )

            TutorialStep(1, "Turn on the accessibility service", state.accessibility, current == 1) {
                Text(
                    "This lets Ultrebo perform your taps and swipes.\n" +
                        "1. Tap the button below.\n" +
                        "2. Open Installed apps (or Downloaded apps) and tap Ultrebo.\n" +
                        "3. Turn it on and tap Allow.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("Open accessibility settings") }
                if (Build.VERSION.SDK_INT >= 33) {
                    Text(
                        "Switch greyed out, or Android says \"Restricted setting\"? Android blocks apps " +
                            "installed outside the Play Store until you allow it once:\n" +
                            "1. Tap Open app info below.\n" +
                            "2. Tap the three dots at the top right and choose Allow restricted settings " +
                            "(if you don't see it, first try turning the service on once so Android shows " +
                            "the restricted message, then come back).\n" +
                            "3. Confirm, go back, and turn the service on.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                        )
                    }) { Text("Open app info") }
                }
            }

            if (needsNotificationPermission) {
                TutorialStep(2, "Allow notifications", state.notifications, current == 2) {
                    Text(
                        "Ultrebo shows a small notification while screen capture is on. " +
                            "Android 13 and newer asks for your OK first. If nothing happens when you tap, " +
                            "the button opens notification settings - turn notifications on there.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = onRequestNotifications) { Text("Allow notifications") }
                }
            }

            HorizontalDivider()
            Text("Optional: screen capture", style = MaterialTheme.typography.titleSmall)
            Text(
                if (state.capture) "On - image and text steps are ready."
                else "Only needed for image and text steps. Android asks again each time Ultrebo is restarted.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (!state.capture) {
                OutlinedButton(onClick = onGrantCapture) { Text("Grant screen capture") }
            }
        }
    }
}

@Composable
private fun TutorialStep(
    number: Int,
    title: String,
    done: Boolean,
    expanded: Boolean,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (done) {
                Icon(Icons.Default.Check, contentDescription = "Done")
            } else {
                Text("$number.", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
        if (expanded && !done) {
            Column(Modifier.padding(start = 28.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
        }
    }
}

/** Shown once the required permissions are on. */
@Composable
private fun ReadyCard(state: SetupState, onGrantCapture: () -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ready", style = MaterialTheme.typography.titleMedium)
            Text(
                "Screen capture ${if (state.capture) "on" else "off"} - only needed for image and text steps.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val svc = MacroAccessibilityService.instance
                    if (svc == null) {
                        Toast.makeText(context, "Enable the accessibility service first", Toast.LENGTH_SHORT).show()
                    } else {
                        svc.overlay.showBubble()
                    }
                }) { Text("Floating controls") }
                if (state.capture) {
                    OutlinedButton(onClick = { ScreenCaptureService.stop(context) }) { Text("Stop capture") }
                } else {
                    OutlinedButton(onClick = onGrantCapture) { Text("Grant screen capture") }
                }
            }
        }
    }
}
