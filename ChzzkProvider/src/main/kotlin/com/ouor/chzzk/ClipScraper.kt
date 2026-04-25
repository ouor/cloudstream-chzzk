package com.ouor.chzzk

/**
 * Best-effort scraper for Chzzk clip pages. The official API capture set in
 * this repo does not include a documented clip play-info endpoint, so we lift
 * the same data the web player uses out of the embedded `__NEXT_DATA__`
 * JSON blob (Next.js convention). When the site structure changes, [parse]
 * returns `null` and the caller surfaces a friendly error.
 *
 * Two extraction strategies are attempted in order:
 *   1. Locate `<script id="__NEXT_DATA__"` and JSON-parse its body, then
 *      hunt for `videoUrl`/`m3u8` inside the embedded clip props.
 *   2. Fall back to a regex sweep across the raw HTML for any `*.m3u8` /
 *      `*.mp4` URL adjacent to a `videoUrl` / `playUrl` key.
 *
 * Both strategies are intentionally permissive — the goal is "extract a
 * playable URL when one is present", not "fully validate Chzzk's response
 * schema".
 */
object ClipScraper {

    data class Clip(
        val title: String? = null,
        val thumbnailUrl: String? = null,
        val channelName: String? = null,
        val durationSec: Int? = null,
        val playbackUrl: String? = null,
    )

    private val NEXT_DATA_REGEX = Regex(
        """<script[^>]*id=["']__NEXT_DATA__["'][^>]*>(.*?)</script>""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
    )

    private val PLAYBACK_URL_REGEXES = listOf(
        Regex(""""(?:videoUrl|playUrl|hlsUrl|m3u8Url)"\s*:\s*"(https?:[^"\\]+)"""),
        Regex(""""path"\s*:\s*"(https?:[^"\\]+\.m3u8[^"\\]*)""""),
    )

    private val MP4_FALLBACK = Regex("""(https?://[^"\s]+\.(?:m3u8|mp4)[^"\s]*)""")

    private val TITLE_REGEX = Regex(""""clipTitle"\s*:\s*"([^"]+)"""")
    private val THUMB_REGEX = Regex(""""thumbnailImageUrl"\s*:\s*"(https?:[^"]+)"""")
    private val CHANNEL_REGEX = Regex(""""channelName"\s*:\s*"([^"]+)"""")
    private val DURATION_REGEX = Regex(""""duration"\s*:\s*(\d+)""")

    fun parse(html: String): Clip? {
        val nextData = NEXT_DATA_REGEX.find(html)?.groupValues?.get(1)
        val haystack = nextData ?: html

        val playbackUrl = PLAYBACK_URL_REGEXES.firstNotNullOfOrNull { regex ->
            regex.find(haystack)?.groupValues?.get(1)?.unescape()
        } ?: MP4_FALLBACK.find(haystack)?.groupValues?.get(1)?.unescape()

        if (playbackUrl == null) return null

        return Clip(
            title = TITLE_REGEX.find(haystack)?.groupValues?.get(1)?.unescape(),
            thumbnailUrl = THUMB_REGEX.find(haystack)?.groupValues?.get(1)?.unescape(),
            channelName = CHANNEL_REGEX.find(haystack)?.groupValues?.get(1)?.unescape(),
            durationSec = DURATION_REGEX.find(haystack)?.groupValues?.get(1)?.toIntOrNull(),
            playbackUrl = playbackUrl,
        )
    }

    /** Decode the JSON-string escapes that survive into the captured payload. */
    private fun String.unescape(): String =
        replace("\\/", "/")
            .replace("\\\"", "\"")
            .replace("\\u0026", "&")
            .replace("\\\\", "\\")
}
