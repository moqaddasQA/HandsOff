package com.moqaddas.handsoff.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads assets/blocklist.txt into a HashSet at startup.
 * isBlocked() walks up the domain hierarchy so a single entry for
 * "appsflyer.com" also blocks "launches.appsflyer.com", "t.appsflyer.com", etc.
 * O(1) per lookup regardless of list size.
 */
@Singleton
class DnsBlocklist @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val domains: HashSet<String> = HashSet(512)

    init { load() }

    private fun load() {
        context.assets.open("blocklist.txt").bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val trimmed = line.trim().lowercase()
                if (trimmed.isNotEmpty() && !trimmed.startsWith('#')) {
                    domains.add(trimmed)
                }
            }
        }
    }

    /**
     * Returns true if [domain] or any of its parent domains is on the blocklist.
     * e.g. "sdk.adjust.com" → checks "sdk.adjust.com" then "adjust.com" → blocked.
     */
    fun isBlocked(domain: String): Boolean {
        var d = domain.lowercase().trimEnd('.')
        while (d.isNotEmpty()) {
            if (domains.contains(d)) return true
            val dot = d.indexOf('.')
            if (dot == -1) break
            d = d.substring(dot + 1)
        }
        return false
    }

    val size: Int get() = domains.size
}
