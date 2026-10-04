package com.ultimate.macrobot.data

import com.ultimate.macrobot.model.Versions
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/**
 * Asks GitHub for the latest release and compares it with the installed version.
 * The only data sent is the request itself; nothing about the user or device is included.
 */
object UpdateChecker {
    private const val API_URL = "https://api.github.com/repos/Ultimate0026/Ultimate/releases/latest"
    private const val PAGE_PREFIX = "https://github.com/"

    /** Downloads are only accepted from this repo's release assets. */
    const val APK_PREFIX = "https://github.com/Ultimate0026/Ultimate/releases/download/"

    data class Update(
        val version: String,
        /** The release page, always available as a manual fallback. */
        val url: String,
        val apkUrl: String? = null,
        /** Lower-case hex SHA-256 of the APK as published by GitHub, when known. */
        val sha256: String? = null,
        val sizeBytes: Long = 0,
    )

    /** Returns the newer release, or null when up to date or anything goes wrong. */
    fun check(currentVersion: String): Update? {
        return try {
            val conn = URL(API_URL).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "Ultrebo")
                if (conn.responseCode != 200) return null
                parseRelease(conn.inputStream.bufferedReader().use { it.readText() }, currentVersion)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Pure parsing of GitHub's "latest release" JSON; split out so it can be unit tested. */
    fun parseRelease(body: String, currentVersion: String): Update? {
        return try {
            val obj = Json.parseToJsonElement(body).jsonObject
            val tag = obj["tag_name"]?.jsonPrimitive?.content ?: return null
            val page = obj["html_url"]?.jsonPrimitive?.content ?: return null
            if (!page.startsWith(PAGE_PREFIX)) return null
            if (!Versions.isNewer(tag, currentVersion)) return null

            val asset = (obj["assets"] as? JsonArray)?.jsonArray
                ?.map { it.jsonObject }
                ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
            val apkUrl = asset?.get("browser_download_url")?.jsonPrimitive?.content
                ?.takeIf { it.startsWith(APK_PREFIX) }
            val sha = asset?.get("digest")?.jsonPrimitive?.content
                ?.removePrefix("sha256:")?.lowercase()
                ?.takeIf { it.length == 64 && it.all { c -> c in '0'..'9' || c in 'a'..'f' } }
            val size = asset?.get("size")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
            Update(tag.removePrefix("v"), page, apkUrl, sha, size)
        } catch (e: Exception) {
            null
        }
    }
}
