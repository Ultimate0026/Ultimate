package com.ultimate.macrobot.data

import com.ultimate.macrobot.model.Versions
import kotlinx.serialization.json.Json
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

    data class Update(val version: String, val url: String)

    /** Returns the newer release, or null when up to date or anything goes wrong. */
    fun check(currentVersion: String): Update? {
        return try {
            val conn = URL(API_URL).openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "MacroBot")
                if (conn.responseCode != 200) return null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = Json.parseToJsonElement(body).jsonObject
                val tag = obj["tag_name"]?.jsonPrimitive?.content ?: return null
                val page = obj["html_url"]?.jsonPrimitive?.content ?: return null
                if (!page.startsWith(PAGE_PREFIX)) return null
                if (Versions.isNewer(tag, currentVersion)) Update(tag.removePrefix("v"), page) else null
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            null
        }
    }
}
