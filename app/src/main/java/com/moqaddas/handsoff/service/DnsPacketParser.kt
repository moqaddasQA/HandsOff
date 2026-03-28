package com.moqaddas.handsoff.service

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Parses a raw IPv4/UDP/DNS packet from the TUN interface and returns either:
 *  - A synthesised NXDOMAIN response (blocked domain)
 *  - A proxied upstream DNS response (allowed domain)
 *  - null if the packet is not a DNS query (caller skips it)
 *
 * Packet layout:
 *   [IPv4 header 20 bytes][UDP header 8 bytes][DNS payload ≥ 12 bytes]
 *
 * Security: no string concatenation builds shell commands here.
 * The upstream DatagramSocket is protected via the [protect] lambda so it
 * bypasses our own TUN and reaches the real network — preventing an infinite loop.
 */
object DnsPacketParser {

    private const val UPSTREAM_IP   = "94.140.14.14"   // AdGuard DNS
    private const val UPSTREAM_PORT = 53
    private const val DNS_PORT      = 53
    private const val PROTO_UDP     = 17
    private const val IP_HDR        = 20
    private const val UDP_HDR       = 8

    data class DnsResult(val packet: ByteArray, val wasBlocked: Boolean)

    fun handle(
        raw: ByteArray,
        length: Int,
        blocklist: DnsBlocklist,
        protect: (DatagramSocket) -> Boolean
    ): DnsResult? {
        if (length < IP_HDR + UDP_HDR + 12) return null

        // ── IPv4 header ──────────────────────────────────────────────────────
        val versionIhl = raw[0].toInt() and 0xFF
        if (versionIhl shr 4 != 4) return null          // not IPv4
        val ihl        = (versionIhl and 0x0F) * 4      // header length in bytes
        val totalLen   = ((raw[2].toInt() and 0xFF) shl 8) or (raw[3].toInt() and 0xFF)
        val protocol   = raw[9].toInt() and 0xFF
        if (protocol != PROTO_UDP) return null

        val srcIp = raw.copyOfRange(12, 16)
        val dstIp = raw.copyOfRange(16, 20)

        // ── UDP header ───────────────────────────────────────────────────────
        val udpBase = ihl
        if (length < udpBase + UDP_HDR) return null
        val srcPort = ((raw[udpBase].toInt() and 0xFF) shl 8) or (raw[udpBase + 1].toInt() and 0xFF)
        val dstPort = ((raw[udpBase + 2].toInt() and 0xFF) shl 8) or (raw[udpBase + 3].toInt() and 0xFF)
        if (dstPort != DNS_PORT) return null

        // ── DNS payload ──────────────────────────────────────────────────────
        val dnsBase = udpBase + UDP_HDR
        val dnsLen  = totalLen - dnsBase
        if (dnsLen < 12 || dnsBase + dnsLen > length) return null

        val dns = raw.copyOfRange(dnsBase, dnsBase + dnsLen)

        // Only handle standard queries (QR=0, Opcode=0)
        val flags = ((dns[2].toInt() and 0xFF) shl 8) or (dns[3].toInt() and 0xFF)
        if (flags and 0x8000 != 0) return null   // QR=1 means it's a response, not a query

        val domain = parseDomain(dns) ?: return null

        return if (blocklist.isBlocked(domain)) {
            val resp = nxdomain(dns)
            DnsResult(wrapPacket(dstIp, srcIp, DNS_PORT, srcPort, resp), wasBlocked = true)
        } else {
            val upstream = forwardUpstream(dns, protect) ?: return null
            DnsResult(wrapPacket(dstIp, srcIp, DNS_PORT, srcPort, upstream), wasBlocked = false)
        }
    }

    // ── DNS helpers ──────────────────────────────────────────────────────────

    /** Reads the QNAME from the DNS question section (starts at byte 12). */
    private fun parseDomain(dns: ByteArray): String? {
        val sb = StringBuilder()
        var pos = 12
        while (pos < dns.size) {
            val labelLen = dns[pos].toInt() and 0xFF
            if (labelLen == 0) break
            if (pos + labelLen >= dns.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            sb.append(String(dns, pos + 1, labelLen, Charsets.US_ASCII))
            pos += labelLen + 1
        }
        return sb.toString().takeIf { it.isNotEmpty() }
    }

    /** Returns a DNS NXDOMAIN response with the same transaction ID and question. */
    private fun nxdomain(query: ByteArray): ByteArray {
        val r = query.copyOf()
        r[2] = 0x81.toByte()   // QR=1 AA=0 TC=0 RD=1
        r[3] = 0x83.toByte()   // RA=1 RCODE=3 (NXDOMAIN)
        // ANCOUNT, NSCOUNT, ARCOUNT remain 0 — no answer records
        return r
    }

    /**
     * Forwards the DNS query to AdGuard upstream using a protected socket.
     * [protect] must be called before send() to prevent the socket from
     * routing back through our own TUN (infinite loop).
     */
    private fun forwardUpstream(
        query: ByteArray,
        protect: (DatagramSocket) -> Boolean
    ): ByteArray? = try {
        val socket = DatagramSocket()
        protect(socket)
        socket.soTimeout = 3_000
        val addr = InetAddress.getByName(UPSTREAM_IP)
        socket.send(DatagramPacket(query, query.size, addr, UPSTREAM_PORT))
        val buf  = ByteArray(4096)
        val recv = DatagramPacket(buf, buf.size)
        socket.receive(recv)
        socket.close()
        buf.copyOf(recv.length)
    } catch (_: Exception) { null }

    // ── IP/UDP packet builder ────────────────────────────────────────────────

    private fun wrapPacket(
        srcIp:   ByteArray,
        dstIp:   ByteArray,
        srcPort: Int,
        dstPort: Int,
        payload: ByteArray
    ): ByteArray {
        val udpLen   = UDP_HDR + payload.size
        val totalLen = IP_HDR + udpLen
        val packet   = ByteArray(totalLen)
        val buf      = ByteBuffer.wrap(packet).order(ByteOrder.BIG_ENDIAN)

        // IPv4 header
        buf.put(0x45.toByte())                    // Version=4, IHL=5 (20 bytes)
        buf.put(0x00.toByte())                    // DSCP/ECN
        buf.putShort(totalLen.toShort())          // Total Length
        buf.putShort(0x0000)                      // Identification
        buf.putShort(0x4000.toShort())            // Flags=DF, Fragment Offset=0
        buf.put(0x40.toByte())                    // TTL = 64
        buf.put(PROTO_UDP.toByte())               // Protocol = UDP
        buf.putShort(0x0000)                      // Checksum — filled in below
        buf.put(srcIp)
        buf.put(dstIp)

        // Fill IP header checksum (ones-complement sum of the 20-byte header)
        val cksum = ipChecksum(packet, 0, IP_HDR)
        packet[10] = (cksum shr 8).toByte()
        packet[11] = (cksum and 0xFF).toByte()

        // UDP header
        buf.putShort(srcPort.toShort())           // Source Port
        buf.putShort(dstPort.toShort())           // Destination Port
        buf.putShort(udpLen.toShort())            // UDP Length
        buf.putShort(0x0000)                      // Checksum = 0 (optional in IPv4)

        buf.put(payload)
        return packet
    }

    /** Standard IPv4 header checksum — ones-complement sum of 16-bit words. */
    private fun ipChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0
        var i   = offset
        while (i < offset + length - 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i   += 2
        }
        // Odd byte at end (shouldn't happen for a 20-byte IP header but handle it)
        if ((length and 1) != 0) sum += (data[offset + length - 1].toInt() and 0xFF) shl 8
        // Fold carries
        while (sum shr 16 != 0) sum = (sum and 0xFFFF) + (sum shr 16)
        return sum.inv() and 0xFFFF
    }
}
