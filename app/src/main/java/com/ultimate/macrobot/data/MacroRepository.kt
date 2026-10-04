// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Ultrevo. See LICENSE and NOTICE.

package com.ultimate.macrobot.data

import android.content.Context
import com.ultimate.macrobot.model.Macro
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Stores macros as JSON in app storage and keeps template images next to them. */
class MacroRepository(context: Context) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = File(context.filesDir, "macros.json")
    private val prefs = context.getSharedPreferences("macrobot", Context.MODE_PRIVATE)
    val templatesDir = File(context.filesDir, "templates").apply { mkdirs() }

    private val _macros = MutableStateFlow(load())
    val macros: StateFlow<List<Macro>> = _macros

    /** The macro being edited; recording, image picking and "start" all target it. */
    private val _activeId = MutableStateFlow(prefs.getString("active", null))
    val activeId: StateFlow<String?> = _activeId

    var updateChecksEnabled: Boolean
        get() = prefs.getBoolean("update_checks", true)
        set(value) { prefs.edit().putBoolean("update_checks", value).apply() }

    fun setActive(id: String?) {
        _activeId.value = id
        prefs.edit().putString("active", id).apply()
    }

    fun active(): Macro? = _macros.value.firstOrNull { it.id == _activeId.value }

    fun get(id: String): Macro? = _macros.value.firstOrNull { it.id == id }

    @Synchronized
    fun add(macro: Macro) {
        _macros.value = _macros.value + macro
        save()
    }

    @Synchronized
    fun update(id: String, transform: (Macro) -> Macro) {
        _macros.value = _macros.value.map { if (it.id == id) transform(it) else it }
        save()
    }

    @Synchronized
    fun delete(id: String) {
        _macros.value.firstOrNull { it.id == id }?.let { it.steps + it.rules }?.forEach { s ->
            s.templateFile?.let { File(templatesDir, it).delete() }
        }
        _macros.value = _macros.value.filter { it.id != id }
        if (_activeId.value == id) setActive(null)
        save()
    }

    fun templatePath(name: String): File = File(templatesDir, name)

    private fun load(): List<Macro> = try {
        if (file.exists()) json.decodeFromString<List<Macro>>(file.readText()).map { it.migrated() } else emptyList()
    } catch (e: Exception) {
        emptyList()
    }

    private fun save() {
        val tmp = File(file.parentFile, "macros.json.tmp")
        tmp.writeText(json.encodeToString(_macros.value))
        tmp.renameTo(file)
    }
}
