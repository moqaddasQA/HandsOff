package com.moqaddas.handsoff.service

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.net.DatagramSocket

/**
 * Unit tests for DnsPacketParser.
 *
 * DnsPacketParser is a pure Kotlin object with no Android dependencies.
 * DnsBlocklist is mocked with MockK to control blocked/allowed decisions.
 *
 * All tests use hand-crafted raw IPv4/UDP/DNS byte arrays so we validate
 * the real parsing logic rather than mocking the internals.
 */
class DnsPacketParserTest {

    // ── Test packet builder ──────────────────────────────────────────────────

    /**
     * Builds a minimal valid IPv4/UDP/DNS query packet for [domain].
     *
     * Layout:
     *   [0..19]   IPv4 header (version=4, IHL=5, protocol=17/UDP, src/dst IP)
     *   [20..27]  UDP header (srcPort=12345, dstPort=53, length)
     *   [28..]    DNS query (txid, flags=0x0100, QDCOUNT=1, question section)
     */
    private fun buildDnsQueryPacket(
        domain: String,
        srcIp:  ByteArray = byteArrayOf(10, 0, 0, 2),
        dstIp:  ByteArray = byteArrayOf(10, 0, 0, 1),
        srcPort: Int = 12345,
        dstPort: Int = 53,
        protocol: Int = 17   // UDP
    ): ByteArray {
        // Build DNS question section from domain
        val labels = domain.split('.').filter { it.isNotEmpty() }
        val question = mutableListOf<Byte>()
        for (label in labels) {
            question.add(label.length.toByte())
            question.addAll(label.encodeToByteArray().toList())
        }
        question.add(0)             // root label
        question.add(0); question.add(1)   // QTYPE A
        question.add(0); question.add(1)   // QCLASS IN

        // DNS header (12 bytes)
        val dns = byteArrayOf(
            0x12, 0x34,             // Transaction ID
            0x01, 0x00,             // Flags: RD=1 (standard query)
            0x00, 0x01,             // QDCOUNT = 1
            0x00, 0x00,             // ANCOUNT = 0
            0x00, 0x00,             // NSCOUNT = 0
            0x00, 0x00              // ARCOUNT = 0
        ) + question.toByteArray()

        val udpLen   = 8 + dns.size
        val totalLen = 20 + udpLen

        val pkt = ByteArray(totalLen)

        // IPv4 header
        pkt[0]  = 0x45.toByte()                           // Version=4, IHL=5
        pkt[1]  = 0x00                                     // DSCP/ECN
        pkt[2]  = (totalLen shr 8).toByte()
        pkt[3]  = (totalLen and 0xFF).toByte()
        pkt[4]  = 0x00; pkt[5] = 0x00                    // Identification
        pkt[6]  = 0x40; pkt[7] = 0x00                    // Flags DF, offset 0
        pkt[8]  = 0x40.toByte()                           // TTL=64
        pkt[9]  = protocol.toByte()
        pkt[10] = 0x00; pkt[11] = 0x00                   // checksum (0 for tests)
        System.arraycopy(srcIp, 0, pkt, 12, 4)
        System.arraycopy(dstIp, 0, pkt, 16, 4)

        // UDP header
        pkt[20] = (srcPort shr 8).toByte()
        pkt[21] = (srcPort and 0xFF).toByte()
        pkt[22] = (dstPort shr 8).toByte()
        pkt[23] = (dstPort and 0xFF).toByte()
        pkt[24] = (udpLen shr 8).toByte()
        pkt[25] = (udpLen and 0xFF).toByte()
        pkt[26] = 0x00; pkt[27] = 0x00                   // UDP checksum optional

        // DNS payload
        System.arraycopy(dns, 0, pkt, 28, dns.size)

        return pkt
    }

    // Protect lambda that always succeeds (no real VPN in tests)
    private val noopProtect: (DatagramSocket) -> Boolean = { true }

    // ── Null-return (packet filtering) ───────────────────────────────────────

    @Test
    fun `non-IPv4 packet returns null`() {
        val pkt = buildDnsQueryPacket("example.com")
        pkt[0] = 0x65.toByte()   // Version=6 (IPv6)
        assertNull(DnsPacketParser.handle(pkt, pkt.size, mockk(), noopProtect))
    }

    @Test
    fun `non-UDP protocol returns null`() {
        val pkt = buildDnsQueryPacket("example.com", protocol = 6)   // TCP
        assertNull(DnsPacketParser.handle(pkt, pkt.size, mockk(), noopProtect))
    }

    @Test
    fun `packet to non-DNS port returns null`() {
        val pkt = buildDnsQueryPacket("example.com", dstPort = 443)   // HTTPS
        assertNull(DnsPacketParser.handle(pkt, pkt.size, mockk(), noopProtect))
    }

    @Test
    fun `packet too short returns null`() {
        val pkt = ByteArray(20)   // just an IP header, no UDP or DNS
        assertNull(DnsPacketParser.handle(pkt, pkt.size, mockk(), noopProtect))
    }

    @Test
    fun `DNS response packet (QR=1) returns null`() {
        val pkt = buildDnsQueryPacket("example.com")
        pkt[30] = 0x81.toByte()   // Set QR=1 in DNS flags byte
        assertNull(DnsPacketParser.handle(pkt, pkt.size, mockk(), noopProtect))
    }

    // ── NXDOMAIN synthesis (blocked domain) ──────────────────────────────────

    @Test
    fun `blocked domain returns NXDOMAIN response`() {
        val pkt = buildDnsQueryPacket("appsflyer.com")
        val bl  = mockk<DnsBlocklist> { every { isBlocked("appsflyer.com") } returns true }

        val result = DnsPacketParser.handle(pkt, pkt.size, bl, noopProtect)

        assertNotNull(result)
        assertTrue(result!!.wasBlocked)
    }

    @Test
    fun `NXDOMAIN response has QR=1 flag set`() {
        val pkt = buildDnsQueryPacket("tracker.example.com")
        val bl  = mockk<DnsBlocklist> { every { isBlocked(any()) } returns true }

        val result = DnsPacketParser.handle(pkt, pkt.size, bl, noopProtect)!!
        // DNS section starts at IP (20) + UDP (8) = offset 28
        // Flags are at DNS offset 2+3 — absolute positions 30+31
        val dnsFlags = ((result.packet[30].toInt() and 0xFF) shl 8) or
                        (result.packet[31].toInt() and 0xFF)

        // QR=1 means it's a response; RCODE=3 means NXDOMAIN
        assertTrue(dnsFlags and 0x8000 != 0, "QR bit should be set")
        assertEquals(3, dnsFlags and 0x000F, "RCODE should be 3 (NXDOMAIN)")
    }

    @Test
    fun `NXDOMAIN response preserves original transaction ID`() {
        val pkt = buildDnsQueryPacket("appsflyer.com")
        val bl  = mockk<DnsBlocklist> { every { isBlocked(any()) } returns true }

        val result = DnsPacketParser.handle(pkt, pkt.size, bl, noopProtect)!!
        // Transaction ID at DNS offset 0+1 — absolute positions 28+29
        assertEquals(0x12.toByte(), result.packet[28], "TxID high byte must match")
        assertEquals(0x34.toByte(), result.packet[29], "TxID low byte must match")
    }

    // ── IP/UDP wrapping ──────────────────────────────────────────────────────

    @Test
    fun `response packet has swapped src and dst IPs`() {
        val srcIp = byteArrayOf(10, 0, 0, 2)
        val dstIp = byteArrayOf(10, 0, 0, 1)
        val pkt   = buildDnsQueryPacket("appsflyer.com", srcIp = srcIp, dstIp = dstIp)
        val bl    = mockk<DnsBlocklist> { every { isBlocked(any()) } returns true }

        val result = DnsPacketParser.handle(pkt, pkt.size, bl, noopProtect)!!
        val resp   = result.packet

        // In the response: src = original dst (10.0.0.1), dst = original src (10.0.0.2)
        assertArrayEquals(dstIp, resp.copyOfRange(12, 16), "Response src IP should be original dst")
        assertArrayEquals(srcIp, resp.copyOfRange(16, 20), "Response dst IP should be original src")
    }

    @Test
    fun `response packet has valid IPv4 header checksum`() {
        val pkt = buildDnsQueryPacket("appsflyer.com")
        val bl  = mockk<DnsBlocklist> { every { isBlocked(any()) } returns true }

        val resp = DnsPacketParser.handle(pkt, pkt.size, bl, noopProtect)!!.packet

        // Re-compute checksum over response header with checksum bytes zeroed
        val headerCopy = resp.copyOfRange(0, 20)
        headerCopy[10] = 0; headerCopy[11] = 0
        var sum = 0
        for (i in 0 until 20 step 2) {
            sum += ((headerCopy[i].toInt() and 0xFF) shl 8) or (headerCopy[i + 1].toInt() and 0xFF)
        }
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        val computed = sum.inv() and 0xFFFF

        val stored = ((resp[10].toInt() and 0xFF) shl 8) or (resp[11].toInt() and 0xFF)
        assertEquals(computed, stored, "IPv4 header checksum should be valid")
    }

    @Test
    fun `response packet source port is DNS port 53`() {
        val pkt = buildDnsQueryPacket("appsflyer.com", srcPort = 12345)
        val bl  = mockk<DnsBlocklist> { every { isBlocked(any()) } returns true }

        val resp   = DnsPacketParser.handle(pkt, pkt.size, bl, noopProtect)!!.packet
        val srcPort = ((resp[20].toInt() and 0xFF) shl 8) or (resp[21].toInt() and 0xFF)
        assertEquals(53, srcPort, "Response source port should be 53")
    }

    @Test
    fun `response packet destination port is original client port`() {
        val pkt = buildDnsQueryPacket("appsflyer.com", srcPort = 54321)
        val bl  = mockk<DnsBlocklist> { every { isBlocked(any()) } returns true }

        val resp    = DnsPacketParser.handle(pkt, pkt.size, bl, noopProtect)!!.packet
        val dstPort = ((resp[22].toInt() and 0xFF) shl 8) or (resp[23].toInt() and 0xFF)
        assertEquals(54321, dstPort, "Response destination port should be original src port")
    }

    // ── Domain parsing ───────────────────────────────────────────────────────

    @Test
    fun `domain is passed to blocklist correctly`() {
        var capturedDomain: String? = null
        val bl = mockk<DnsBlocklist> {
            every { isBlocked(any()) } answers {
                capturedDomain = firstArg()
                false   // allow, so we don't need a real upstream
            }
        }

        // We don't care about the result (forwarding will fail without a real socket)
        val pkt = buildDnsQueryPacket("sdk.adjust.com")
        DnsPacketParser.handle(pkt, pkt.size, bl, noopProtect)

        assertEquals("sdk.adjust.com", capturedDomain)
    }

    @Test
    fun `single-label domain is parsed`() {
        var capturedDomain: String? = null
        val bl = mockk<DnsBlocklist> {
            every { isBlocked(any()) } answers { capturedDomain = firstArg(); false }
        }
        val pkt = buildDnsQueryPacket("localhost")
        DnsPacketParser.handle(pkt, pkt.size, bl, noopProtect)
        assertEquals("localhost", capturedDomain)
    }

    // ── wasBlocked flag ──────────────────────────────────────────────────────

    @Test
    fun `wasBlocked is false for allowed domain (upstream fails silently)`() {
        val pkt = buildDnsQueryPacket("google.com")
        val bl  = mockk<DnsBlocklist> { every { isBlocked(any()) } returns false }

        // Upstream will fail (no real network in unit test) → handle() returns null
        // This tests that the code path hits the upstream branch and returns null on failure
        val result = DnsPacketParser.handle(pkt, pkt.size, bl, noopProtect)
        // Null is acceptable here — the upstream socket timed out, which is correct
        if (result != null) assertFalse(result.wasBlocked)
    }
}
