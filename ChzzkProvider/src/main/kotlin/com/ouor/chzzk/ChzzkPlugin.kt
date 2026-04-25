package com.ouor.chzzk

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.ouor.chzzk.auth.ChzzkAuth
import com.ouor.chzzk.settings.ChzzkSettings
import com.ouor.chzzk.settings.ChzzkSettingsFragment

@CloudstreamPlugin
class ChzzkPlugin : Plugin() {
    private var activity: AppCompatActivity? = null

    override fun load(context: Context) {
        // Pre-load auth + plugin preferences so the very first ChzzkApi.get()
        // call is already authenticated and respects the user's LLHLS choice.
        ChzzkAuth.bootstrap(context)
        ChzzkSettings.bootstrap(context)
        activity = context as? AppCompatActivity
        registerMainAPI(ChzzkProvider())

        // Tapping the gear icon next to the provider in CloudStream's Extension
        // list opens our BottomSheet settings.
        openSettings = open@{
            val act = activity ?: return@open
            ChzzkSettingsFragment(this).show(act.supportFragmentManager, "chzzk-settings")
        }
    }
}
