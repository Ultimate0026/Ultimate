// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Ultrevo. See LICENSE and NOTICE.

package com.ultimate.macrobot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscordWebhookTest {
    private val hook = "https://discord.com/api/webhooks/123456789012345678/abcdefghijklmnopqrstuvwxyzABCDEF_-0123"

    @Test
    fun realLookingWebhooksAreAccepted() {
        for (url in listOf(
            hook,
            hook.replace("discord.com", "discordapp.com"),
            hook.replace("discord.com", "ptb.discord.com"),
            hook.replace("discord.com", "canary.discord.com"),
            hook.replace("/api/", "/api/v10/"),
            "  $hook  ",
        )) assertTrue(url, DiscordWebhook.isValid(url))
    }

    @Test
    fun anythingElseIsRefusedSoScreenshotsNeverGoElsewhere() {
        for (url in listOf(
            "",
            "not a url",
            hook.replace("https", "http"),
            hook.replace("discord.com", "example.com"),
            hook.replace("discord.com", "discord.com.evil.example"),
            "https://evil.example/https://discord.com/api/webhooks/123456789012345678/abcdefghijklmnopqrstuvwxyz",
            "$hook/extra",
            "$hook?thread_id=1",
            "https://discord.com/api/webhooks/abc/def",
        )) {
            assertFalse(url, DiscordWebhook.isValid(url))
            assertEquals("That isn't a Discord webhook address.", DiscordWebhook.post(url, "hi", null, "test"))
        }
    }

    @Test
    fun messageNamesCannotPingAnyoneOrBreakTheLine() {
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2, 3)
        val body = String(DiscordWebhook.buildBody("BOUND", "Found \"@everyone\"\nsecond line", jpeg), Charsets.ISO_8859_1)
        assertEquals(3, Regex("--BOUND").findAll(body).count())
        assertTrue(body.endsWith("--BOUND--\r\n"))
        assertTrue(body.contains("\"allowed_mentions\":{\"parse\":[]}"))
        assertTrue(body.contains("\"content\":\"Found \\\"@everyone\\\" second line\""))
        assertTrue(body.contains("name=\"files[0]\"; filename=\"screenshot.jpg\""))
        assertTrue(body.contains(String(jpeg, Charsets.ISO_8859_1)))
    }

    @Test
    fun aMessageWithoutAScreenshotHasNoFilePart() {
        val body = String(DiscordWebhook.buildBody("B", "hello", null), Charsets.UTF_8)
        assertFalse(body.contains("files[0]"))
        assertTrue(body.contains("hello"))
    }

    @Test
    fun cleanTextIsOneShortLine() {
        assertEquals("a b c d", DiscordWebhook.cleanText("a\r\nb\tc\u0000d"))
        assertEquals(50, DiscordWebhook.cleanText("x".repeat(5000), 50).length)
    }

    @Test
    fun problemsAreExplainedInPlainWords() {
        assertTrue(DiscordWebhook.problemFor(404).contains("doesn't exist"))
        assertTrue(DiscordWebhook.problemFor(401).contains("doesn't exist"))
        assertTrue(DiscordWebhook.problemFor(429).contains("slow down"))
        assertTrue(DiscordWebhook.problemFor(413).contains("too big"))
        assertTrue(DiscordWebhook.problemFor(500).contains("error 500"))
    }
}
