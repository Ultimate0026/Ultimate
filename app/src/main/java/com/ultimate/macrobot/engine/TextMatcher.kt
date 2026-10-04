// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Ultrevo. See LICENSE and NOTICE.

package com.ultimate.macrobot.engine

/** One word found by OCR with its bounding box in screen pixels. */
data class OcrWord(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int)

data class TextMatch(val x: Int, val y: Int, val score: Double)

/**
 * Finds typed text among OCR words. Comparison ignores case, spaces and punctuation, and tolerates
 * small OCR mistakes: [threshold] is the minimum similarity (1.0 = identical).
 */
object TextMatcher {
    fun normalize(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    fun similarity(a: String, b: String): Double {
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / maxLen
    }

    private fun levenshtein(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev; prev = cur; cur = tmp
        }
        return prev[b.length]
    }

    /** Best match across all [lines] of OCR words, as the centre of the matched words. */
    fun find(lines: List<List<OcrWord>>, target: String, threshold: Double): TextMatch? {
        val wanted = normalize(target)
        if (wanted.isEmpty()) return null
        val wordCount = target.trim().split(Regex("\\s+")).size
        var best: TextMatch? = null
        for (line in lines) {
            // OCR sometimes splits or joins words, so also try windows one word shorter/longer.
            for (size in maxOf(1, wordCount - 1)..(wordCount + 1)) {
                if (size > line.size) continue
                for (start in 0..line.size - size) {
                    val window = line.subList(start, start + size)
                    val score = similarity(wanted, normalize(window.joinToString("") { it.text }))
                    val current = best
                    if (score >= threshold && (current == null || score > current.score)) {
                        val left = window.minOf { it.left }
                        val right = window.maxOf { it.right }
                        val top = window.minOf { it.top }
                        val bottom = window.maxOf { it.bottom }
                        best = TextMatch((left + right) / 2, (top + bottom) / 2, score)
                    }
                }
            }
        }
        return best
    }
}
