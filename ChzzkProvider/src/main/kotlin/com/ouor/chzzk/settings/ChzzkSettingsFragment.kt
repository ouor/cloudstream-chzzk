package com.ouor.chzzk.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.plugins.Plugin
import com.ouor.chzzk.R
import com.ouor.chzzk.api.ChzzkApi
import com.ouor.chzzk.auth.ChzzkAuth
import com.ouor.chzzk.databinding.FragmentChzzkSettingsBinding

/**
 * Provider settings UI. Surfaces:
 *   - NID_AUT / NID_SES cookie input (pre-filled with the current snapshot,
 *     password-style masked) plus a save button that wires the cookies into
 *     [ChzzkAuth] and invalidates the response cache so the next call is
 *     authenticated.
 *   - LLHLS preference toggle, persisted to [ChzzkSettings].
 *   - Cache invalidation button (drops the in-process LRU).
 *
 * Resources (layout xml, strings) are packaged into the .cs3 by the
 * cloudstream gradle plugin and loaded through the plugin classloader, so
 * the standard ViewBinding.inflate path works without any classloader
 * acrobatics.
 */
class ChzzkSettingsFragment(
    @Suppress("unused") private val plugin: Plugin,
) : BottomSheetDialogFragment() {

    private var binding: FragmentChzzkSettingsBinding? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentChzzkSettingsBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val b = binding ?: return

        // Pre-fill from current snapshots so the user can see what is stored.
        val authSnap = ChzzkAuth.current()
        val settingsSnap = ChzzkSettings.current()
        b.chzzkInputAut.setText(authSnap.nidAut.orEmpty())
        b.chzzkInputSes.setText(authSnap.nidSes.orEmpty())
        b.chzzkSwitchLlhls.isChecked = settingsSnap.preferLowLatency
        refreshLoginBadge(authSnap.isLoggedIn)

        b.chzzkBtnSaveLogin.setOnClickListener {
            val aut = b.chzzkInputAut.text?.toString().orEmpty().trim()
            val ses = b.chzzkInputSes.text?.toString().orEmpty().trim()
            if (aut.isBlank() || ses.isBlank()) {
                toast(R.string.chzzk_toast_login_blank)
                return@setOnClickListener
            }
            ChzzkAuth.set(aut, ses)
            ChzzkApi.invalidateCache()
            refreshLoginBadge(loggedIn = true)
            toast(R.string.chzzk_toast_saved)
        }

        b.chzzkBtnLogout.setOnClickListener {
            ChzzkAuth.clear()
            ChzzkApi.invalidateCache()
            b.chzzkInputAut.setText("")
            b.chzzkInputSes.setText("")
            refreshLoginBadge(loggedIn = false)
            toast(R.string.chzzk_toast_logged_out)
        }

        b.chzzkSwitchLlhls.setOnCheckedChangeListener { _, isChecked ->
            ChzzkSettings.setPreferLowLatency(isChecked)
        }

        b.chzzkBtnClearCache.setOnClickListener {
            ChzzkApi.invalidateCache()
            toast(R.string.chzzk_toast_cleared)
        }
    }

    private fun refreshLoginBadge(loggedIn: Boolean) {
        val b = binding ?: return
        b.chzzkLoginStatus.setText(
            if (loggedIn) R.string.chzzk_login_status_in else R.string.chzzk_login_status_out,
        )
    }

    private fun toast(stringRes: Int) {
        Toast.makeText(requireContext(), stringRes, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        binding = null
        super.onDestroyView()
    }
}
