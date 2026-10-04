// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Ultrevo. See LICENSE and NOTICE.

package com.ultimate.macrobot.data

import com.ultimate.macrobot.model.Step
import com.ultimate.macrobot.model.StepType
import com.ultimate.macrobot.model.WatchAction
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** The file is not a usable rule pack. The message is safe to show to the user. */
class RulePackException(message: String) : Exception(message)

/** A rule read from a pack. Its picture (if any) is not saved anywhere yet. */
class ImportedRule(val rule: Step, val png: ByteArray?)

@Serializable
data class PackRule(
    val type: String,
    val name: String = "",
    val enabled: Boolean = true,
    val text: String = "",
    val threshold: Float = 0.8f,
    val delayAfterMs: Long = 500,
    val durationMs: Long = 60,
    val onSeen: String = "CONTINUE",
    val tapOnSeen: Boolean = true,
    val image: String? = null,
)

@Serializable
data class PackManifest(
    val format: Int = 0,
    val platform: String = "",
    val appVersion: String = "",
    val name: String = "",
    val rules: List<PackRule> = emptyList(),
)

/**
 * Shareable rule packs: a macro's rules, with their pictures, in one `.ultrebo-rules` file (a zip with
 * `rules.json` and `images/`). Packs come from strangers, so reading one never writes files by name: it
 * only picks out the exact entries it expects, limits sizes, and clamps every number.
 */
object RulePack {
    const val EXTENSION = ".ultrebo-rules"
    const val FORMAT = 1
    const val PLATFORM = "android"
    const val MAX_RULES = 100
    const val MAX_IMAGE_BYTES = 5 * 1024 * 1024
    const val MAX_JSON_BYTES = 512 * 1024
    const val MAX_TOTAL_BYTES = 30 * 1024 * 1024

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val imageName = Regex("images/[0-9]{1,4}\\.png")

    /** Writes [rules] (picture and text rules only) to [out]. Returns how many were written. */
    fun write(
        rules: List<Step>,
        name: String,
        appVersion: String,
        readTemplate: (String) -> ByteArray?,
        out: OutputStream,
    ): Int {
        val finders = rules.filter { it.needsScreen }
        if (finders.isEmpty()) throw RulePackException("This macro has no rules to share yet.")
        val images = LinkedHashMap<String, ByteArray>()
        val entries = finders.map { r ->
            var member: String? = null
            if (r.isImage) {
                val file = r.templateFile ?: throw RulePackException("The rule \"${r.title()}\" has no image picked.")
                val bytes = readTemplate(file)
                    ?: throw RulePackException("The picture for the rule \"${r.title()}\" is missing.")
                member = "images/${images.size + 1}.png"
                images[member] = bytes
            }
            PackRule(
                type = if (r.isImage) "IMAGE" else "TEXT",
                name = r.name,
                enabled = r.enabled,
                text = r.text,
                threshold = r.threshold,
                delayAfterMs = r.delayAfterMs,
                durationMs = r.durationMs,
                onSeen = r.onSeen.name,
                tapOnSeen = r.tapOnSeen,
                image = member,
            )
        }
        val manifest = PackManifest(FORMAT, PLATFORM, appVersion, name, entries)
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("rules.json"))
            zip.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            for ((member, bytes) in images) {
                zip.putNextEntry(ZipEntry(member))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return entries.size
    }

    /**
     * Reads a pack from [input]. [cleanPng] turns a picture's bytes into a re-encoded PNG, or null when the
     * bytes are not a usable image. Returns the pack's name and its rules; nothing is saved here.
     */
    fun read(input: InputStream, cleanPng: (ByteArray) -> ByteArray?): Pair<String, List<ImportedRule>> {
        val files = readEntries(input)
        val manifestBytes = files["rules.json"] ?: throw RulePackException("This file isn't a rule pack.")
        val manifest = try {
            json.decodeFromString<PackManifest>(manifestBytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw RulePackException("This file isn't a rule pack.")
        }
        if (manifest.platform.isNotEmpty() && manifest.platform != PLATFORM) {
            throw RulePackException("This rule pack was made for the computer version of Ultrebo, so it can't be used on a phone.")
        }
        if (manifest.format != FORMAT) {
            throw RulePackException("This rule pack was made by a newer version of Ultrebo. Update Ultrebo and try again.")
        }
        if (manifest.rules.isEmpty()) throw RulePackException("This rule pack has no rules in it.")
        if (manifest.rules.size > MAX_RULES) {
            throw RulePackException("This rule pack has too many rules (the limit is $MAX_RULES).")
        }

        val imported = manifest.rules.map { item ->
            val isImage = when (item.type) {
                "IMAGE" -> true
                "TEXT" -> false
                else -> throw RulePackException("Rule packs can only contain picture and text rules.")
            }
            val text = item.text.trim().take(200)
            if (!isImage && text.isEmpty()) throw RulePackException("A text rule in this file has no text.")
            var png: ByteArray? = null
            if (isImage) {
                val member = item.image
                if (member == null || !imageName.matches(member)) {
                    throw RulePackException("A picture rule in this file has no picture.")
                }
                val raw = files[member] ?: throw RulePackException("The file is missing a picture.")
                png = cleanPng(raw) ?: throw RulePackException("One of the pictures in the file is not a valid image.")
            }
            val action = WatchAction.entries.firstOrNull { it.name == item.onSeen } ?: WatchAction.CONTINUE
            val rule = Step(
                name = item.name.take(80),
                type = if (isImage) StepType.TAP_IMAGE else StepType.TAP_TEXT,
                durationMs = item.durationMs.coerceIn(1L, 5000L),
                delayAfterMs = item.delayAfterMs.coerceIn(0L, 3_600_000L),
                enabled = item.enabled,
                text = if (isImage) "" else text,
                threshold = if (item.threshold.isNaN()) 0.8f else item.threshold.coerceIn(0.1f, 1f),
                repeat = 1,
                watch = true,
                onSeen = action,
                tapOnSeen = item.tapOnSeen,
            )
            ImportedRule(rule, png)
        }
        return manifest.name.trim().take(80) to imported
    }

    /** Reads only `rules.json` and `images/N.png` entries, never more than the size limits allow. */
    private fun readEntries(input: InputStream): Map<String, ByteArray> {
        val files = HashMap<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val wanted = !entry.isDirectory && (entry.name == "rules.json" || imageName.matches(entry.name))
                    if (!wanted) continue
                    val limit = if (entry.name == "rules.json") MAX_JSON_BYTES else MAX_IMAGE_BYTES
                    val buffer = ByteArrayOutputStream()
                    val chunk = ByteArray(8192)
                    while (true) {
                        val n = zip.read(chunk)
                        if (n < 0) break
                        if (buffer.size() + n > limit) throw RulePackException("${entry.name} is too large for a rule pack.")
                        total += n
                        if (total > MAX_TOTAL_BYTES) throw RulePackException("This rule pack is too large.")
                        buffer.write(chunk, 0, n)
                    }
                    files[entry.name] = buffer.toByteArray()
                }
            }
        } catch (e: RulePackException) {
            throw e
        } catch (e: Exception) {
            throw RulePackException("This file isn't a rule pack.")
        }
        return files
    }
}
