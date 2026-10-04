package com.ultimate.macrobot.data

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads the new APK from this repo's GitHub release and hands it to Android's installer.
 * Android only installs it over the current app if it is signed with the same key, and still asks
 * the user to confirm. The download is also checked against the SHA-256 GitHub publishes.
 */
object ApkInstaller {
    /** Returns an error message, or null when the installer was started successfully. */
    suspend fun downloadAndInstall(
        context: Context,
        update: UpdateChecker.Update,
        onProgress: (Float) -> Unit,
    ): String? = withContext(Dispatchers.IO) {
        val apkUrl = update.apkUrl ?: return@withContext "This release has no downloadable APK."
        if (!apkUrl.startsWith(UpdateChecker.APK_PREFIX)) return@withContext "Unexpected download address."

        try {
            val dir = File(context.cacheDir, "update").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "Ultrebo-${update.version}.apk")

            val digest = MessageDigest.getInstance("SHA-256")
            val conn = URL(apkUrl).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15000
                conn.readTimeout = 30000
                conn.setRequestProperty("User-Agent", "Ultrebo")
                if (conn.responseCode != 200) return@withContext "Download failed (HTTP ${conn.responseCode})."
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: update.sizeBytes
                var read = 0L
                conn.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            read += n
                            if (total > 0) onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }

            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (update.sha256 != null && update.sha256 != actual) {
                file.delete()
                return@withContext "The downloaded file did not match GitHub's checksum. Try again."
            }

            install(context, file)
            null
        } catch (e: java.util.concurrent.CancellationException) {
            throw e
        } catch (e: Exception) {
            "Update failed: ${e.message ?: e.javaClass.simpleName}"
        }
    }

    private fun install(context: Context, file: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName) // only ever an update to this app
        if (Build.VERSION.SDK_INT >= 31) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            file.inputStream().use { input ->
                session.openWrite("macrobot.apk", 0, file.length()).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            val callback = Intent(context, InstallResultReceiver::class.java)
            val pending = PendingIntent.getBroadcast(
                context, sessionId, callback,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(pending.intentSender)
        }
    }
}

/** Receives the installer's progress; shows Android's confirmation screen when it is needed. */
class InstallResultReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (confirm != null) context.startActivity(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // Android restarts the updated app
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown error"
                Toast.makeText(context, "Update not installed: $message", Toast.LENGTH_LONG).show()
            }
        }
    }
}
