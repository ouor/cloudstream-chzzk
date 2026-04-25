package com.ouor.chzzk.auth

import android.content.Context
import android.content.SharedPreferences

/**
 * Holds the NAVER session cookies needed for personalised Chzzk endpoints
 * (my-info, follows, 19+ playback). Cookies live in SharedPreferences so they
 * survive process death; an in-memory snapshot is loaded on plugin start.
 *
 * The Plugin should call [bootstrap] from `Plugin.load()` to wire up the
 * SharedPreferences-backed store. UI for entering cookies will land in
 * Group K (settings fragment); for now advanced users can populate the
 * preferences via adb or a companion app:
 *
 *     adb shell run-as <pkg> sh -c \
 *       'echo \"<...>\" > /data/data/<pkg>/shared_prefs/chzzk_auth.xml'
 *
 * Or from CloudStream's settings UI once Group K wires the entry points.
 */
object ChzzkAuth {
    private const val PREFS_NAME = "chzzk_auth"
    private const val KEY_NID_AUT = "nid_aut"
    private const val KEY_NID_SES = "nid_ses"
    private const val KEY_BA_UUID = "ba_uuid"

    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var snapshot: Snapshot = Snapshot()

    data class Snapshot(
        val nidAut: String? = null,
        val nidSes: String? = null,
        val baUuid: String? = null,
    ) {
        val isLoggedIn: Boolean get() = !nidAut.isNullOrBlank() && !nidSes.isNullOrBlank()
    }

    fun bootstrap(context: Context) {
        val store = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = store
        snapshot = Snapshot(
            nidAut = store.getString(KEY_NID_AUT, null),
            nidSes = store.getString(KEY_NID_SES, null),
            baUuid = store.getString(KEY_BA_UUID, null) ?: generateBaUuid().also {
                store.edit().putString(KEY_BA_UUID, it).apply()
            },
        )
    }

    fun current(): Snapshot = snapshot

    fun set(nidAut: String?, nidSes: String?) {
        val store = prefs ?: return
        store.edit()
            .putString(KEY_NID_AUT, nidAut?.takeIf { it.isNotBlank() })
            .putString(KEY_NID_SES, nidSes?.takeIf { it.isNotBlank() })
            .apply()
        snapshot = snapshot.copy(nidAut = nidAut, nidSes = nidSes)
    }

    fun clear() = set(null, null)

    /** Build a Cookie header value from the current snapshot, or null if empty. */
    fun cookieHeader(): String? {
        val s = snapshot
        val parts = buildList {
            s.baUuid?.let { add("ba.uuid=$it") }
            s.nidAut?.let { add("NID_AUT=$it") }
            s.nidSes?.let { add("NID_SES=$it") }
        }
        return if (parts.isEmpty()) null else parts.joinToString("; ")
    }

    private fun generateBaUuid(): String {
        // Pure-Kotlin UUIDv4 — avoids dragging in java.util.UUID just for this.
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        bytes[6] = (bytes[6].toInt() and 0x0f or 0x40).toByte()
        bytes[8] = (bytes[8].toInt() and 0x3f or 0x80).toByte()
        val hex = bytes.joinToString("") { "%02x".format(it) }
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }
}
