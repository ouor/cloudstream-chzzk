package com.ouor.chzzk

/**
 * Compact codec for storing pagination cursors inside [MainPageRequest.data].
 *
 * Format: `<key>?<k1>=<v1>&<k2>=<v2>` — a single string we can stash in the
 * MainPageRequest data slot and parse back. The leading `<key>` is the
 * mainPage section identifier (e.g. `GAME/League_of_Legends`), the rest is
 * the cursor payload.
 *
 * For first-page calls the cursor is empty (just `<key>`); subsequent pages
 * encode whatever the API returned in `page.next`.
 */
internal object Cursor {
    fun decode(data: String): Pair<String, Map<String, String>> {
        val idx = data.indexOf('?')
        if (idx < 0) return data to emptyMap()
        val key = data.substring(0, idx)
        val params = data.substring(idx + 1)
            .split('&')
            .filter { it.isNotEmpty() }
            .mapNotNull {
                val eq = it.indexOf('=')
                if (eq < 0) null else it.substring(0, eq) to it.substring(eq + 1)
            }
            .toMap()
        return key to params
    }

    fun encode(key: String, params: Map<String, String?>): String {
        val nonNull = params.filterValues { !it.isNullOrEmpty() }
        if (nonNull.isEmpty()) return key
        return key + "?" + nonNull.entries.joinToString("&") { (k, v) -> "$k=$v" }
    }
}
