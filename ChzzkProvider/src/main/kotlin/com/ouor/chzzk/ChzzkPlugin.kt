package com.ouor.chzzk

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class ChzzkPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(ChzzkProvider())
    }
}
