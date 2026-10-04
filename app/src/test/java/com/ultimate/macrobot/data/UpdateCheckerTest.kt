package com.ultimate.macrobot.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateCheckerTest {
    private val sha = "0b307de37bc8783868b737dd3aca7e58af1e2507f5c2d38e9555fec70a7008e8"

    private fun release(tag: String, apkUrl: String = "${UpdateChecker.APK_PREFIX}$tag/MacroBot-$tag.apk") = """
        {"tag_name":"$tag","html_url":"https://github.com/Ultimate0026/Ultimate/releases/tag/$tag",
         "assets":[{"name":"MacroBot-$tag.apk","browser_download_url":"$apkUrl","size":1234,"digest":"sha256:$sha"}]}
    """.trimIndent()

    @Test
    fun newerReleaseYieldsApkChecksumAndSize() {
        val u = UpdateChecker.parseRelease(release("v0.2.0"), "0.1.0")
        assertNotNull(u)
        assertEquals("0.2.0", u!!.version)
        assertEquals("${UpdateChecker.APK_PREFIX}v0.2.0/MacroBot-v0.2.0.apk", u.apkUrl)
        assertEquals(sha, u.sha256)
        assertEquals(1234L, u.sizeBytes)
    }

    @Test
    fun sameVersionIsNotAnUpdate() {
        assertNull(UpdateChecker.parseRelease(release("v0.1.0"), "0.1.0"))
    }

    @Test
    fun downloadsFromOtherHostsAreIgnoredButReleasePageStillOffered() {
        val u = UpdateChecker.parseRelease(release("v0.2.0", "https://evil.example/MacroBot.apk"), "0.1.0")
        assertNotNull(u)
        assertNull(u!!.apkUrl)
    }

    @Test
    fun releaseWithoutApkStillOffersThePage() {
        val body = """{"tag_name":"v0.2.0","html_url":"https://github.com/Ultimate0026/Ultimate/releases/tag/v0.2.0","assets":[]}"""
        val u = UpdateChecker.parseRelease(body, "0.1.0")
        assertNotNull(u)
        assertNull(u!!.apkUrl)
    }

    @Test
    fun malformedOrForeignPagesAreRejected() {
        assertNull(UpdateChecker.parseRelease("not json", "0.1.0"))
        assertNull(UpdateChecker.parseRelease("""{"tag_name":"v9","html_url":"https://evil.example/x"}""", "0.1.0"))
    }
}
