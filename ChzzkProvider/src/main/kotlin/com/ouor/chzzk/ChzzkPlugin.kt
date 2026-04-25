package com.ouor.chzzk

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.ouor.chzzk.auth.ChzzkAuth

@CloudstreamPlugin
class ChzzkPlugin : Plugin() {
    override fun load(context: Context) {
        // Pre-load cached login cookies so the first ChzzkApi.get() call is
        // already authenticated. Without this, the snapshot is empty and the
        // first request hits Chzzk anonymously even when credentials exist.
        ChzzkAuth.bootstrap(context)
        registerMainAPI(ChzzkProvider())
    }
}
