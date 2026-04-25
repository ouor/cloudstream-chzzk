package com.ouor.chzzk.api

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.app
import com.lagradost.nicehttp.NiceResponse

object ChzzkApi {
    private val DEFAULT_HEADERS = mapOf(
        "Accept" to "application/json, text/plain, */*",
        "Accept-Language" to "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
        "Origin" to "https://chzzk.naver.com",
        "Referer" to "https://chzzk.naver.com/",
        "Front-Client-Platform-Type" to "PC",
        "Front-Client-Product-Type" to "web",
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36",
    )

    suspend fun get(url: String, extraHeaders: Map<String, String> = emptyMap()): NiceResponse {
        return app.get(url, headers = DEFAULT_HEADERS + extraHeaders)
    }

    fun checkOk(code: Int, message: String?, what: String) {
        if (code != 200) {
            throw ErrorLoadingException("Chzzk $what failed: code=$code message=${message ?: "<null>"}")
        }
    }
}
