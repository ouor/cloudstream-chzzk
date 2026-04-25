package com.ouor.chzzk.settings

import android.content.res.Resources
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.lagradost.cloudstream3.plugins.Plugin
import com.ouor.chzzk.api.ChzzkApi
import com.ouor.chzzk.auth.ChzzkAuth

/**
 * Provider settings UI built **programmatically** in Kotlin — no XML, no
 * generated `R` class, no ViewBinding. The cloudstream gradle plugin's
 * `make` task only bundles the Kotlin compile output into the .cs3 dex,
 * dropping everything javac produced (R$layout, R$id, ViewBinding stubs,
 * etc.). Running with `requiresResources = true` and accessing
 * `R.layout.fragment_chzzk_settings` therefore crashes with
 * `NoClassDefFoundError: Lcom/ouor/chzzk/R$layout;` at fragment inflation.
 *
 * Building views in code sidesteps the entire javac-vs-Kotlin packaging
 * problem and lets the fragment stay 100% inside the Kotlin compile graph.
 * Strings are inline literals (Korean) — no R.string lookups either.
 *
 * Surfaces:
 *   - NID_AUT / NID_SES cookie input → wires into [ChzzkAuth] + invalidates
 *     the response cache.
 *   - LLHLS preference → persisted to [ChzzkSettings].
 *   - Cache invalidation button → drops the in-process LRU.
 */
class ChzzkSettingsFragment(
    @Suppress("unused") private val plugin: Plugin,
) : BottomSheetDialogFragment() {

    private var loginStatus: TextView? = null
    private var inputAut: EditText? = null
    private var inputSes: EditText? = null
    private var switchLlhls: CheckBox? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val ctx = inflater.context
        val pad = dp(20)

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        // -- Header --------------------------------------------------------
        root += headerText("치지직 플러그인 설정", sp = 20f, marginBottomDp = 16)

        // -- Login section -------------------------------------------------
        root += sectionLabel("로그인 (NID 쿠키)")
        root += smallText(
            "naver.com에 로그인한 브라우저의 개발자 도구 → Application → Cookies → " +
                    "chzzk.naver.com 에서 NID_AUT, NID_SES 값을 복사해 붙여넣으세요. " +
                    "로그인하면 19+ 콘텐츠와 팔로잉 라이브 섹션을 사용할 수 있습니다.",
            marginBottomDp = 12,
        )
        loginStatus = smallText("", marginBottomDp = 8)
        inputAut = passwordInput(hint = "NID_AUT").also { root += wrapWithLabel("NID_AUT", it, marginBottomDp = 8) }
        inputSes = passwordInput(hint = "NID_SES").also { root += wrapWithLabel("NID_SES", it, marginBottomDp = 12) }

        val loginRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = dp(20) }
        }
        val btnSave = Button(ctx).apply {
            text = "저장 및 로그인"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .also { it.marginEnd = dp(8) }
            setOnClickListener { onSaveLogin() }
        }
        val btnLogout = Button(ctx).apply {
            text = "로그아웃"
            setOnClickListener { onLogout() }
        }
        loginRow += btnSave
        loginRow += btnLogout
        root += loginRow

        // -- Playback section ---------------------------------------------
        root += sectionLabel("재생 옵션")
        switchLlhls = CheckBox(ctx).apply {
            text = "저지연 (LLHLS) 우선 재생"
            setOnCheckedChangeListener { _, isChecked -> ChzzkSettings.setPreferLowLatency(isChecked) }
        }
        root += switchLlhls!!
        root += smallText(
            "활성화 시 일반 HLS 대신 저지연 HLS 변형을 플레이어 메뉴 상단에 노출합니다.",
            marginBottomDp = 20,
        )

        // -- Cache section -------------------------------------------------
        root += sectionLabel("캐시")
        root += smallText(
            "채널 메타·도네이션 랭킹 등 5분 캐시를 즉시 삭제합니다. 로그인 직후 적용을 위해 사용하세요.",
            marginBottomDp = 8,
        )
        root += Button(ctx).apply {
            text = "메모리 캐시 비우기"
            setOnClickListener { onClearCache() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = dp(20) }
        }

        // -- Status section ------------------------------------------------
        root += sectionLabel("지원 기능")
        root += smallText(
            "• 라이브 / 다시보기 HLS 재생\n" +
                    "• 클립 재생 (api-videohub.naver.com play-info)\n" +
                    "• 검색 (#태그, live:, vod:, channel: 접두어)\n" +
                    "• 카테고리·홈·파트너·방송 예정 섹션\n" +
                    "• 도네이션·로그파워·카페·상점 메타데이터",
            marginBottomDp = 12,
        )

        // Pre-fill from current snapshots.
        val authSnap = ChzzkAuth.current()
        inputAut?.setText(authSnap.nidAut.orEmpty())
        inputSes?.setText(authSnap.nidSes.orEmpty())
        switchLlhls?.isChecked = ChzzkSettings.current().preferLowLatency
        refreshLoginBadge(authSnap.isLoggedIn)

        return ScrollView(ctx).apply { addView(root) }
    }

    // ------------------------------------------------------------- handlers

    private fun onSaveLogin() {
        val aut = inputAut?.text?.toString().orEmpty().trim()
        val ses = inputSes?.text?.toString().orEmpty().trim()
        if (aut.isBlank() || ses.isBlank()) {
            toast("NID_AUT, NID_SES 둘 다 입력해주세요")
            return
        }
        ChzzkAuth.set(aut, ses)
        ChzzkApi.invalidateCache()
        refreshLoginBadge(loggedIn = true)
        toast("저장되었습니다")
    }

    private fun onLogout() {
        ChzzkAuth.clear()
        ChzzkApi.invalidateCache()
        inputAut?.setText("")
        inputSes?.setText("")
        refreshLoginBadge(loggedIn = false)
        toast("로그아웃되었습니다")
    }

    private fun onClearCache() {
        ChzzkApi.invalidateCache()
        toast("캐시를 비웠습니다")
    }

    private fun refreshLoginBadge(loggedIn: Boolean) {
        loginStatus?.text = if (loggedIn) "✅ 로그인됨" else "⛔ 비로그인 상태"
    }

    private fun toast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroyView() {
        loginStatus = null
        inputAut = null
        inputSes = null
        switchLlhls = null
        super.onDestroyView()
    }

    // -------------------------------------------------------------- view helpers

    private fun dp(value: Int): Int =
        (value * Resources.getSystem().displayMetrics.density).toInt()

    private fun headerText(text: String, sp: Float, marginBottomDp: Int): TextView {
        val ctx = requireContext()
        return TextView(ctx).apply {
            this.text = text
            textSize = sp
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = dp(marginBottomDp) }
        }
    }

    private fun sectionLabel(text: String): TextView {
        val ctx = requireContext()
        return TextView(ctx).apply {
            this.text = text
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also {
                it.topMargin = dp(8)
                it.bottomMargin = dp(6)
            }
        }
    }

    private fun smallText(text: String, marginBottomDp: Int = 0): TextView {
        val ctx = requireContext()
        return TextView(ctx).apply {
            this.text = text
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = dp(marginBottomDp) }
        }
    }

    private fun passwordInput(hint: String): EditText {
        val ctx = requireContext()
        return EditText(ctx).apply {
            this.hint = hint
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
        }
    }

    /**
     * Wraps an EditText with a small label TextView above it inside a vertical
     * LinearLayout — no TextInputLayout dependency (which would also need
     * Material theme attributes that the host activity may not provide).
     */
    private fun wrapWithLabel(label: String, input: EditText, marginBottomDp: Int): View {
        val ctx = requireContext()
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).also { it.bottomMargin = dp(marginBottomDp) }
            addView(TextView(ctx).apply {
                text = label
                textSize = 12f
            })
            addView(input)
        }
    }

    /** Sugar so `layout += view` reads naturally. */
    private operator fun LinearLayout.plusAssign(view: View) {
        addView(view)
    }
}
