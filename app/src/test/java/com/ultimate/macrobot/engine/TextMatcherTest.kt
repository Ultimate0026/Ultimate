// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Ultrevo. See LICENSE and NOTICE.

package com.ultimate.macrobot.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TextMatcherTest {
    private fun word(text: String, left: Int, top: Int = 100, right: Int = left + 60, bottom: Int = 140) =
        OcrWord(text, left, top, right, bottom)

    private val screen = listOf(
        listOf(word("Wave", 10), word("12", 80)),
        listOf(word("Are", 300), word("you", 370), word("still", 440), word("there?", 510)),
        listOf(word("I'm", 300, 400, 360, 440), word("here", 370, 400, 430, 440)),
    )

    @Test
    fun findsPhraseAndReturnsItsCentre() {
        val m = TextMatcher.find(screen, "I'm here", 0.85)
        assertNotNull(m)
        assertEquals((300 + 430) / 2, m!!.x)
        assertEquals((400 + 440) / 2, m.y)
    }

    @Test
    fun ignoresCasePunctuationAndSpacing() {
        assertNotNull(TextMatcher.find(screen, "IM HERE", 0.9))
        assertNotNull(TextMatcher.find(screen, "are you still there", 0.9))
    }

    @Test
    fun toleratesSmallOcrMistakes() {
        // One wrong letter out of six: similarity is about 0.83.
        val misread = listOf(listOf(word("I'm", 0), word("hera", 70)))
        assertNotNull(TextMatcher.find(misread, "I'm here", 0.8))
        assertNull(TextMatcher.find(misread, "I'm here", 0.95))
    }

    @Test
    fun missingOrBlankTextIsNotFound() {
        assertNull(TextMatcher.find(screen, "game over", 0.85))
        assertNull(TextMatcher.find(screen, "   ", 0.5))
        assertNull(TextMatcher.find(emptyList(), "wave", 0.5))
    }

    @Test
    fun wordSplitDifferencesStillMatch() {
        val joined = listOf(listOf(word("I'mhere", 0)))
        assertNotNull(TextMatcher.find(joined, "I'm here", 0.9))
    }
}
