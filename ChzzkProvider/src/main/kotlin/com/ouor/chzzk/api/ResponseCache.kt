package com.ouor.chzzk.api

/**
 * Tiny in-memory cache with per-entry TTL. Used by [ChzzkApi] to memoize
 * cheap-but-frequent GETs like channel metadata, donation rank, and chat
 * rules — these change on the order of minutes, but a busy mainPage can
 * fan out the same channelId multiple times within seconds.
 *
 * Entries are keyed by URL string. The cache is bounded ([MAX_ENTRIES]) so
 * a long-running session does not bloat indefinitely; on overflow the
 * oldest insertion is evicted.
 */
internal object ResponseCache {
    private const val MAX_ENTRIES = 256
    private const val DEFAULT_TTL_MS = 5 * 60 * 1000L

    private data class Entry(val body: String, val expiresAt: Long)

    private val store = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > MAX_ENTRIES
    }

    @Synchronized
    fun get(url: String): String? {
        val entry = store[url] ?: return null
        if (System.currentTimeMillis() > entry.expiresAt) {
            store.remove(url)
            return null
        }
        return entry.body
    }

    @Synchronized
    fun put(url: String, body: String, ttlMs: Long = DEFAULT_TTL_MS) {
        store[url] = Entry(body, System.currentTimeMillis() + ttlMs)
    }

    @Synchronized
    fun clear() = store.clear()
}
