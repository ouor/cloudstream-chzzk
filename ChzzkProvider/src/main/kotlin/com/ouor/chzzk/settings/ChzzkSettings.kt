package com.ouor.chzzk.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Plugin-wide preferences not related to authentication. NID cookies are
 * stored separately in [com.ouor.chzzk.auth.ChzzkAuth] so that this file
 * can be read without touching login state.
 *
 * Currently exposes:
 *  - [preferLowLatency] — when true, [ChzzkProvider.emitMediaLinks] orders
 *    LLHLS variants ahead of standard HLS so the player picks low-latency
 *    by default.
 *
 * The Plugin should call [bootstrap] from `Plugin.load()` before the
 * provider is registered so that defaults are loaded synchronously.
 */
object ChzzkSettings {
    private const val PREFS_NAME = "chzzk_settings"
    private const val KEY_PREFER_LLHLS = "prefer_llhls"

    @Volatile private var prefs: SharedPreferences? = null
    @Volatile private var snapshot: Snapshot = Snapshot()

    data class Snapshot(
        val preferLowLatency: Boolean = false,
    )

    fun bootstrap(context: Context) {
        val store = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = store
        snapshot = Snapshot(
            preferLowLatency = store.getBoolean(KEY_PREFER_LLHLS, false),
        )
    }

    fun current(): Snapshot = snapshot

    fun setPreferLowLatency(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_PREFER_LLHLS, enabled)?.apply()
        snapshot = snapshot.copy(preferLowLatency = enabled)
    }
}
