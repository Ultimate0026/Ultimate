// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Ultrevo. See LICENSE and NOTICE.

package com.ultimate.macrobot.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Posting a message with a screenshot to a Discord webhook. The webhook address is a secret (anyone who has it can
 * post in the channel), so it is only ever kept in the app's private settings, never in a macro or a rule pack.
 */
object DiscordWebhook {
    private val pattern = Regex(
        "https://(?:(?:ptb|canary)\\.)?(?:discord|discordapp)\\.com/api(?:/v\\d+)?/webhooks/\\d{5,25}/[A-Za-z0-9_\\-]{20,200}",
    )
    private val json = Json { encodeDefaults = true }

    @Serializable
    private class AllowedMentions(val parse: List<String> = emptyList())

    @Serializable
    private class Payload(
        val content: String,
        @SerialName("allowed_mentions") val allowedMentions: AllowedMentions = AllowedMentions(),
    )

    /**
     * True for an address that looks like a Discord webhook. Anything else is refused, so a screenshot is never
     * posted to some other website because of a mistyped or pasted-in address.
     */
    fun isValid(url: String): Boolean = pattern.matches(url.trim())

    /** Makes a name safe to put in a message: one line, no control characters, not too long. */
    fun cleanText(text: String, limit: Int = 200): String =
        text.replace(Regex("[\\u0000-\\u001f\\u007f]+"), " ").trim().take(limit)

    /** The request body (multipart/form-data). Names in the message can't ping anyone: `allowed_mentions` is empty. */
    fun buildBody(boundary: String, message: String, jpeg: ByteArray?): ByteArray {
        val payload = json.encodeToString(Payload(cleanText(message, 1900)))
        val out = java.io.ByteArrayOutputStream()
        fun text(s: String) = out.write(s.toByteArray(Charsets.UTF_8))
        text("--$boundary\r\nContent-Disposition: form-data; name=\"payload_json\"\r\nContent-Type: application/json\r\n\r\n$payload\r\n")
        if (jpeg != null) {
            text("--$boundary\r\nContent-Disposition: form-data; name=\"files[0]\"; filename=\"screenshot.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n")
            out.write(jpeg)
            text("\r\n")
        }
        text("--$boundary--\r\n")
        return out.toByteArray()
    }

    /** What to tell the user for an HTTP answer from Discord. */
    fun problemFor(code: Int): String = when (code) {
        401, 404 -> "Discord says this webhook doesn't exist any more. Make a new one and paste it in again."
        429 -> "Discord asked Ultrebo to slow down, so a screenshot was skipped."
        413 -> "The screenshot was too big for Discord."
        else -> "Discord answered with error $code."
    }

    /** Sends one message (with a screenshot when [jpeg] is given). Blocking: call it off the main thread. Returns null on success, or what went wrong. */
    fun post(url: String, message: String, jpeg: ByteArray?, userAgent: String): String? {
        if (!isValid(url)) return "That isn't a Discord webhook address."
        val boundary = "ultrebo" + UUID.randomUUID().toString().replace("-", "")
        val body = buildBody(boundary, message, jpeg)
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url.trim()).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 20_000
                setFixedLengthStreamingMode(body.size)
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                setRequestProperty("User-Agent", userAgent) // Discord turns away clients with no name
            }
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            if (code in 200..299) null else problemFor(code)
        } catch (e: IOException) {
            "Couldn't reach Discord (${e.javaClass.simpleName})."
        } finally {
            connection?.disconnect()
        }
    }
}
