package com.ultimate.macrobot.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
    @Test
    fun newerVersionsAreDetected() {
        assertTrue(Versions.isNewer("v0.1.1", "0.1.0"))
        assertTrue(Versions.isNewer("v0.10.0", "0.9.9"))
        assertTrue(Versions.isNewer("1.0.0", "0.9.9"))
    }

    @Test
    fun sameOrOlderVersionsAreNotUpdates() {
        assertFalse(Versions.isNewer("v0.1.0", "0.1.0"))
        assertFalse(Versions.isNewer("v1.0", "1.0.0"))
        assertFalse(Versions.isNewer("v0.1.0", "0.1.1"))
    }

    @Test
    fun garbageDoesNotCrash() {
        assertFalse(Versions.isNewer("latest", "0.1.0"))
    }
}
