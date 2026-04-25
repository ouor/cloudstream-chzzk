package com.ouor.chzzk.api

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.app
import com.lagradost.nicehttp.NiceResponse
import com.ouor.chzzk.auth.ChzzkAuth

/**
 * Centralized HTTP client for Chzzk. All Provider code should funnel through
 * here so retry/back-off, auth cookie injection, and response caching apply
 * uniformly.
 *
 * Note: tracker telemetry endpoints (e.g. `POST /service/live-status`) are
 * intentionally NOT exposed here — the plugin never opts users into
 * client-side telemetry. The web client's tracking calls are simply skipped.
 */
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

    /**
     * URL prefixes whose responses can be safely cached for [DEFAULT_CACHE_TTL_MS].
     * These endpoints return slow-changing data (channel metadata, weekly
     * rankings, chat rules) so memoizing them avoids redundant fan-out
     * during a single mainPage refresh.
     *
     * Live-status, live-detail, and playback URLs are deliberately NOT
     * cached because they expose freshness-sensitive fields (concurrent
     * viewer count, hdnts auth tokens with short TTL).
     */
    private val CACHEABLE_PATH_PREFIXES = listOf(
        "/service/v1/channels/",       // channel metadata + sub-resources
        "/service/v1/banners",
        "/service/v1/program-schedules/",
        "/service/v1/topics",
        "/service/v1/streamer-partners/",
        "/service/v2/nickname/color/codes",
        "/commercial/v1/channels/",     // donation rank, etc.
        "/commercial/v1/streamer-shop/",
    )

    private const val MAX_RETRIES = 3
    private val RETRY_DELAYS_MS = longArrayOf(0L, 400L, 1200L)

    /**
     * Lightweight sleep for retry back-off. Uses Thread.sleep directly; `app.get`
     * runs on an IO dispatcher so blocking the thread for a few hundred ms is
     * the same shape NiceHttp itself uses internally. We avoid
     * `kotlinx.coroutines.delay` so we do not need a hard dependency on the
     * coroutines API surface, which the cloudstream stub jar does not expose.
     */
    private fun sleepFor(ms: Long) {
        if (ms <= 0L) return
        try { Thread.sleep(ms) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
    }

    /**
     * Live request, no cache. Returns the raw NiceResponse so callers needing
     * headers/status can introspect them. The retry/back-off and auth header
     * injection still apply.
     */
    suspend fun get(url: String, extraHeaders: Map<String, String> = emptyMap()): NiceResponse {
        val authHeaders = ChzzkAuth.cookieHeader()?.let { mapOf("Cookie" to it) } ?: emptyMap()
        val finalHeaders = DEFAULT_HEADERS + authHeaders + extraHeaders

        var lastError: Throwable? = null
        for (attempt in 0 until MAX_RETRIES) {
            try {
                val response = app.get(url, headers = finalHeaders)
                // Retry on server-side errors and rate-limits, never on 4xx
                // client errors (those are deterministic and won't recover).
                if (response.code in 500..599 || response.code == 429) {
                    lastError = RuntimeException("HTTP ${response.code}")
                    sleepFor(RETRY_DELAYS_MS[attempt])
                    continue
                }
                return response
            } catch (e: Throwable) {
                lastError = e
                if (attempt < MAX_RETRIES - 1) {
                    sleepFor(RETRY_DELAYS_MS[attempt])
                }
            }
        }
        throw ErrorLoadingException("Chzzk request failed after $MAX_RETRIES attempts: $url (${lastError?.message ?: "unknown"})")
    }

    /**
     * Cached GET that returns just the body text. Most callers immediately
     * call `.text` on the response anyway — this variant lets cache hits
     * skip constructing a NiceResponse entirely. Cacheability is decided by
     * URL path prefix; non-cacheable URLs fall through to a regular [get].
     */
    suspend fun getText(url: String, extraHeaders: Map<String, String> = emptyMap()): String {
        if (isCacheable(url)) {
            ResponseCache.get(url)?.let { return it }
        }
        val response = get(url, extraHeaders)
        val body = response.text
        if (isCacheable(url) && response.code == 200) {
            ResponseCache.put(url, body)
        }
        return body
    }

    fun checkOk(code: Int, message: String?, what: String) {
        if (code != 200) {
            throw ErrorLoadingException("Chzzk $what failed: code=$code message=${message ?: "<null>"}")
        }
    }

    /** Drop the in-memory cache. Useful from settings UI when a user updates auth. */
    fun invalidateCache() = ResponseCache.clear()

    private fun isCacheable(url: String): Boolean =
        CACHEABLE_PATH_PREFIXES.any { url.contains(it) }
}
