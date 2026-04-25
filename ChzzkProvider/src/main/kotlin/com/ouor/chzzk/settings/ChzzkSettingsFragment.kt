package com.ouor.chzzk.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.lagradost.cloudstream3.plugins.Plugin
import com.ouor.chzzk.R
import com.ouor.chzzk.api.ChzzkApi
import com.ouor.chzzk.auth.ChzzkAuth

/**
 * Provider settings UI. Surfaces:
 *   - NID_AUT / NID_SES cookie input (pre-filled with the current snapshot,
 *     password-style masked) plus a save button that wires the cookies into
 *     [ChzzkAuth] and invalidates the response cache so the next call is
 *     authenticated.
 *   - LLHLS preference toggle, persisted to [ChzzkSettings].
 *   - Cache invalidation button (drops the in-process LRU).
 *
 * Uses raw [findViewById] rather than ViewBinding because the cloudstream
 * gradle plugin's `make` task does not include the Java-compiled binding
 * output in the .cs3 dex, even though `viewBinding = true` causes the
 * binding class to be generated. Symptom was a runtime
 * `NoClassDefFoundError: ...databinding.FragmentChzzkSettingsBinding`.
 * findViewById sidesteps the issue at the cost of a ~30-line view lookup
 * block — a worthwhile trade for not depending on a broken intermediate.
 */
class ChzzkSettingsFragment(
    @Suppress("unused") private val plugin: Plugin,
) : BottomSheetDialogFragment() {

    private var loginStatusView: TextView? = null
    private var inputAut: TextInputEditText? = null
    private var inputSes: TextInputEditText? = null
    private var switchLlhls: SwitchMaterial? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        return inflater.inflate(R.layout.fragment_chzzk_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loginStatusView = view.findViewById(R.id.chzzkLoginStatus)
        inputAut = view.findViewById(R.id.chzzkInputAut)
        inputSes = view.findViewById(R.id.chzzkInputSes)
        switchLlhls = view.findViewById(R.id.chzzkSwitchLlhls)
        val btnSaveLogin = view.findViewById<MaterialButton>(R.id.chzzkBtnSaveLogin)
        val btnLogout = view.findViewById<MaterialButton>(R.id.chzzkBtnLogout)
        val btnClearCache = view.findViewById<MaterialButton>(R.id.chzzkBtnClearCache)

        // Pre-fill from current snapshots so the user can see what is stored.
        val authSnap = ChzzkAuth.current()
        val settingsSnap = ChzzkSettings.current()
        inputAut?.setText(authSnap.nidAut.orEmpty())
        inputSes?.setText(authSnap.nidSes.orEmpty())
        switchLlhls?.isChecked = settingsSnap.preferLowLatency
        refreshLoginBadge(authSnap.isLoggedIn)

        btnSaveLogin.setOnClickListener {
            val aut = inputAut?.text?.toString().orEmpty().trim()
            val ses = inputSes?.text?.toString().orEmpty().trim()
            if (aut.isBlank() || ses.isBlank()) {
                toast(R.string.chzzk_toast_login_blank)
                return@setOnClickListener
            }
            ChzzkAuth.set(aut, ses)
            ChzzkApi.invalidateCache()
            refreshLoginBadge(loggedIn = true)
            toast(R.string.chzzk_toast_saved)
        }

        btnLogout.setOnClickListener {
            ChzzkAuth.clear()
            ChzzkApi.invalidateCache()
            inputAut?.setText("")
            inputSes?.setText("")
            refreshLoginBadge(loggedIn = false)
            toast(R.string.chzzk_toast_logged_out)
        }

        switchLlhls?.setOnCheckedChangeListener { _, isChecked ->
            ChzzkSettings.setPreferLowLatency(isChecked)
        }

        btnClearCache.setOnClickListener {
            ChzzkApi.invalidateCache()
            toast(R.string.chzzk_toast_cleared)
        }
    }

    private fun refreshLoginBadge(loggedIn: Boolean) {
        loginStatusView?.setText(
            if (loggedIn) R.string.chzzk_login_status_in else R.string.chzzk_login_status_out,
        )
    }

    private fun toast(stringRes: Int) {
        Toast.makeText(requireContext(), stringRes, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        loginStatusView = null
        inputAut = null
        inputSes = null
        switchLlhls = null
        super.onDestroyView()
    }
}
