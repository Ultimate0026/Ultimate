package com.ultimate.macrobot.model

object Versions {
    /** True when [remote] (e.g. "v1.2.0") is a higher version than [local] (e.g. "1.1.9"). */
    fun isNewer(remote: String, local: String): Boolean {
        val r = parse(remote)
        val l = parse(local)
        for (i in 0 until maxOf(r.size, l.size)) {
            val a = r.getOrElse(i) { 0 }
            val b = l.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }

    private fun parse(version: String): List<Int> =
        version.trim().removePrefix("v").removePrefix("V").substringBefore('-')
            .split('.')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
}
