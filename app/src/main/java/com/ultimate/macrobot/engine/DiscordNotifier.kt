// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Ultrevo. See LICENSE and NOTICE.

package com.ultimate.macrobot.engine

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.ultimate.macrobot.BuildConfig
import com.ultimate.macrobot.MacroBotApp
import com.ultimate.macrobot.data.DiscordWebhook
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

/**
 * Sends the screenshot of a found image or text to the Discord webhook, in the background: a slow or unreachable
 * Discord never holds up the macro. Each step may send at most once every few seconds, and only a few
 * screenshots wait their turn; more than that are dropped rather than piling up in memory.
 */
object DiscordNotifier {
    private const val MIN_GAP_MS = 5_000L
    private const val MAX_WAITING = 3
    private const val MAX_SIDE = 2560
    private const val PROBLEM_GAP_MS = 30_000L

    private class Item(val url: String, val message: String, val shot: Bitmap?)

    private val throttle = Throttle(MIN_GAP_MS)
    private val queue = Channel<Item>(MAX_WAITING)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false
    private var lastProblemAt = 0L

    private val userAgent get() = "DiscordBot (https://github.com/Ultrevo/Ultrebo, ${BuildConfig.VERSION_NAME})"

    /** True when a valid webhook has been saved. */
    fun configured(): Boolean = DiscordWebhook.isValid(MacroBotApp.repo.webhookUrl)

    /** Queues a screenshot. [key] says which step it is for. False when nothing was queued. */
    fun send(key: String, message: String, shot: Bitmap?): Boolean {
        val url = MacroBotApp.repo.webhookUrl.trim()
        if (!DiscordWebhook.isValid(url) || !throttle.ready(key)) return false
        if (!queue.trySend(Item(url, message, shot)).isSuccess) return false
        throttle.mark(key)
        ensureStarted()
        return true
    }

    @Synchronized
    private fun ensureStarted() {
        if (started) return
        started = true
        scope.launch { work() }
    }

    /** For the "Send test" button: a text-only message, sent right away. Blocking. */
    fun sendTest(url: String): String? =
        DiscordWebhook.post(url, "Ultrebo is connected. Screenshots will appear here.", null, userAgent)

    private suspend fun work() {
        for (item in queue) {
            try {
                val problem = DiscordWebhook.post(item.url, item.message, item.shot?.let { toJpeg(it) }, userAgent)
                if (problem != null) report(problem)
            } catch (e: Exception) {
                report("Couldn't send to Discord (${e.javaClass.simpleName}).")
            } catch (e: OutOfMemoryError) {
                report("The phone ran out of memory while sending a screenshot.")
            }
            delay(1_000) // Discord allows only a few messages every few seconds
        }
    }

    private fun toJpeg(bitmap: Bitmap): ByteArray {
        val longest = maxOf(bitmap.width, bitmap.height)
        val sized = if (longest > MAX_SIDE) {
            val scale = MAX_SIDE.toFloat() / longest
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else {
            bitmap
        }
        try {
            return ByteArrayOutputStream().also { sized.compress(Bitmap.CompressFormat.JPEG, 85, it) }.toByteArray()
        } finally {
            if (sized !== bitmap) sized.recycle()
        }
    }

    private fun report(problem: String) {
        Log.w("Ultrebo", problem)
        val now = System.currentTimeMillis()
        if (now - lastProblemAt < PROBLEM_GAP_MS) return
        lastProblemAt = now
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(MacroBotApp.appContext, problem, Toast.LENGTH_LONG).show()
        }
    }
}
