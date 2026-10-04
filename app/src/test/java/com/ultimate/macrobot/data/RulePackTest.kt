// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Ultrevo. See LICENSE and NOTICE.

package com.ultimate.macrobot.data

import com.ultimate.macrobot.model.Step
import com.ultimate.macrobot.model.StepType
import com.ultimate.macrobot.model.WatchAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RulePackTest {
    private val pic = byteArrayOf(1, 2, 3, 4, 5)

    /** Stands in for the phone's image decoder: any bytes are accepted unless they are empty. */
    private val clean: (ByteArray) -> ByteArray? = { if (it.isEmpty()) null else it }

    private fun image(name: String, priority: Int) =
        Step(name = name, type = StepType.TAP_IMAGE, templateFile = "$name.png", priority = priority, watch = true)

    private fun text(name: String, text: String, priority: Int) =
        Step(
            name = name, type = StepType.TAP_TEXT, text = text, priority = priority, watch = true,
            onSeen = WatchAction.RESTART, delayAfterMs = 900, threshold = 0.7f, tapOnSeen = false,
        )

    private fun pack(rules: List<Step>): ByteArray {
        val out = ByteArrayOutputStream()
        RulePack.write(rules, "Tower farm", "0.1.5", { pic }, out)
        return out.toByteArray()
    }

    private fun zip(entries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, bytes) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun failure(bytes: ByteArray, clean: (ByteArray) -> ByteArray? = this.clean): String =
        try {
            RulePack.read(ByteArrayInputStream(bytes), clean)
            fail("expected the pack to be refused")
            ""
        } catch (e: RulePackException) {
            e.message ?: ""
        }

    private fun json(body: String) = zip(mapOf("rules.json" to body.toByteArray()))

    @Test
    fun exportThenImportKeepsTheRulesAndMakesNewOnes() {
        val original = listOf(text("AFK", "I'm here", 20), image("Claim", 10))
        val (name, imported) = RulePack.read(ByteArrayInputStream(pack(original)), clean)
        assertEquals("Tower farm", name)
        assertEquals(2, imported.size)
        val afk = imported[0].rule
        assertEquals("AFK", afk.name)
        assertEquals("I'm here", afk.text)
        assertEquals(WatchAction.RESTART, afk.onSeen)
        assertEquals(900L, afk.delayAfterMs)
        assertEquals(0.7f, afk.threshold, 0.0001f)
        assertFalse(afk.tapOnSeen)
        assertTrue(afk.watch)
        assertNotEquals(original[0].id, afk.id)
        assertNull(imported[0].png)
        assertEquals(StepType.TAP_IMAGE, imported[1].rule.type)
        assertTrue(imported[1].png!!.contentEquals(pic))
        assertNull(imported[1].rule.templateFile) // the app saves the picture and fills this in
    }

    @Test
    fun nothingToShare() {
        val out = ByteArrayOutputStream()
        try {
            RulePack.write(emptyList(), "x", "0.1.5", { pic }, out)
            fail("expected an error")
        } catch (e: RulePackException) {
            assertTrue(e.message!!.contains("no rules"))
        }
        try {
            RulePack.write(listOf(image("A", 10)), "x", "0.1.5", { null }, out)
            fail("expected an error")
        } catch (e: RulePackException) {
            assertTrue(e.message!!.contains("missing"))
        }
    }

    @Test
    fun refusesFilesThatAreNotPacks() {
        assertTrue(failure("not a zip".toByteArray()).contains("isn't a rule pack"))
        assertTrue(failure(zip(mapOf("other.txt" to pic))).contains("isn't a rule pack"))
        assertTrue(failure(json("{broken")).contains("isn't a rule pack"))
        assertTrue(failure(json("""{"format":2,"rules":[{"type":"TEXT","text":"hi"}]}""")).contains("newer version"))
        assertTrue(failure(json("""{"format":1,"rules":[]}""")).contains("no rules"))
        assertTrue(failure(json("""{"format":1,"rules":[{"type":"TAP"}]}""")).contains("only contain picture and text"))
        assertTrue(failure(json("""{"format":1,"rules":[{"type":"TEXT","text":"  "}]}""")).contains("no text"))
    }

    @Test
    fun aPackFromTheComputerVersionIsExplained() {
        val message = failure(json("""{"format":1,"platform":"desktop","rules":[{"type":"TEXT","text":"hi"}]}"""))
        assertTrue(message.contains("computer version"))
    }

    @Test
    fun tooManyRulesAreRefused() {
        val rules = (1..RulePack.MAX_RULES + 1).joinToString(",") { """{"type":"TEXT","text":"a$it"}""" }
        assertTrue(failure(json("""{"format":1,"rules":[$rules]}""")).contains("too many"))
    }

    @Test
    fun pictureProblemsAreRefused() {
        val rule = """{"format":1,"rules":[{"type":"IMAGE","image":"images/1.png"}]}"""
        // picture missing from the file
        assertTrue(failure(json(rule)).contains("missing a picture"))
        // not a usable image
        assertTrue(failure(zip(mapOf("rules.json" to rule.toByteArray(), "images/1.png" to pic)), { null }).contains("not a valid image"))
        // a name that tries to leave the folder, or no name at all
        val sneaky = """{"format":1,"rules":[{"type":"IMAGE","image":"../../evil.png"}]}"""
        assertTrue(failure(zip(mapOf("rules.json" to sneaky.toByteArray(), "../../evil.png" to pic))).contains("no picture"))
        assertTrue(failure(json("""{"format":1,"rules":[{"type":"IMAGE"}]}""")).contains("no picture"))
    }

    @Test
    fun oversizedEntriesAreRefused() {
        val big = ByteArray(RulePack.MAX_IMAGE_BYTES + 1)
        val rule = """{"format":1,"rules":[{"type":"IMAGE","image":"images/1.png"}]}"""
        assertTrue(failure(zip(mapOf("rules.json" to rule.toByteArray(), "images/1.png" to big))).contains("too large"))
    }

    @Test
    fun hostileNumbersAndFieldsAreCleaned() {
        val body = """{"format":1,"platform":"android","name":"${"n".repeat(500)}","rules":[
            {"type":"TEXT","text":"hi","threshold":99,"delayAfterMs":-5,"durationMs":1000000000,
             "onSeen":"nonsense","name":"${"x".repeat(500)}","enabled":false,"id":"steal-this","templateFile":"../../etc/passwd"}]}"""
        val (name, imported) = RulePack.read(ByteArrayInputStream(json(body)), clean)
        val rule = imported.single().rule
        assertEquals(80, name.length)
        assertEquals(1.0f, rule.threshold, 0.0001f)
        assertEquals(0L, rule.delayAfterMs)
        assertEquals(5000L, rule.durationMs)
        assertEquals(WatchAction.CONTINUE, rule.onSeen)
        assertEquals(80, rule.name.length)
        assertFalse(rule.enabled)
        assertTrue(rule.watch)
        assertEquals(1, rule.repeat)
        assertNotEquals("steal-this", rule.id)
        assertNull(rule.templateFile)
    }
}
