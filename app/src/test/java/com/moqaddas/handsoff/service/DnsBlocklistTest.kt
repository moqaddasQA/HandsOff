package com.moqaddas.handsoff.service

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for DnsBlocklist.
 *
 * Android Context + AssetManager are mocked with MockK so this runs on the JVM
 * without a device or Robolectric. The blocklist content is provided inline.
 */
class DnsBlocklistTest {

    private lateinit var blocklist: DnsBlocklist

    companion object {
        /** Minimal blocklist — enough to cover all test cases. */
        private val BLOCKLIST_CONTENT = """
            # tracker networks
            appsflyer.com
            adjust.com
            doubleclick.net
            scorecardresearch.com
        """.trimIndent()
    }

    @BeforeEach
    fun setUp() {
        val mockContext = mockk<Context>()
        val mockAssets  = mockk<AssetManager>()
        every { mockContext.assets } returns mockAssets
        every { mockAssets.open("blocklist.txt") } returns BLOCKLIST_CONTENT.byteInputStream()
        blocklist = DnsBlocklist(mockContext)
    }

    // ── Exact match ──────────────────────────────────────────────────────────

    @Test
    fun `exact domain match returns blocked`() {
        assertTrue(blocklist.isBlocked("appsflyer.com"))
    }

    @Test
    fun `domain not in list returns allowed`() {
        assertFalse(blocklist.isBlocked("google.com"))
    }

    // ── Parent domain walk ───────────────────────────────────────────────────

    @Test
    fun `subdomain of blocked parent returns blocked`() {
        // "sdk.appsflyer.com" → walks up to "appsflyer.com" → hit
        assertTrue(blocklist.isBlocked("sdk.appsflyer.com"))
    }

    @Test
    fun `deep subdomain of blocked parent returns blocked`() {
        // "launches.sdk.appsflyer.com" → walks: launches.sdk → sdk → appsflyer.com → hit
        assertTrue(blocklist.isBlocked("launches.sdk.appsflyer.com"))
    }

    @Test
    fun `subdomain of safe domain returns allowed`() {
        assertFalse(blocklist.isBlocked("mail.google.com"))
    }

    // ── Case insensitivity ───────────────────────────────────────────────────

    @Test
    fun `uppercase domain is blocked`() {
        assertTrue(blocklist.isBlocked("AppsFlyer.COM"))
    }

    @Test
    fun `mixed case subdomain is blocked`() {
        assertTrue(blocklist.isBlocked("SDK.AppsFlyer.Com"))
    }

    // ── Trailing dot (FQDN from DNS wire format) ─────────────────────────────

    @Test
    fun `domain with trailing dot is blocked`() {
        // DNS wire format includes a trailing dot — isBlocked must strip it
        assertTrue(blocklist.isBlocked("appsflyer.com."))
    }

    @Test
    fun `subdomain with trailing dot is blocked`() {
        assertTrue(blocklist.isBlocked("sdk.appsflyer.com."))
    }

    // ── Comment and blank-line handling ──────────────────────────────────────

    @Test
    fun `comment lines are not treated as domains`() {
        // The blocklist has "# tracker networks" — that should NOT be a domain
        assertFalse(blocklist.isBlocked("# tracker networks"))
        assertFalse(blocklist.isBlocked("tracker networks"))
    }

    // ── Size ─────────────────────────────────────────────────────────────────

    @Test
    fun `size matches number of non-comment lines`() {
        // Our inline blocklist has 4 domain lines, 1 comment, 1 blank
        assertEquals(4, blocklist.size)
    }
}
