package com.ouor.chzzk

object Urls {
    const val WEB_BASE = "https://chzzk.naver.com"

    fun channel(channelId: String) = "$WEB_BASE/$channelId"
    fun live(channelId: String) = "$WEB_BASE/live/$channelId"
    fun video(videoNo: Long) = "$WEB_BASE/video/$videoNo"
    fun clip(clipUID: String) = "$WEB_BASE/clips/$clipUID"

    private val LIVE_REGEX = Regex("""/live/([0-9a-f]{32})""")
    private val VIDEO_REGEX = Regex("""/video/(\d+)""")
    private val CLIP_REGEX = Regex("""/clips/([A-Za-z0-9]+)""")
    private val CHANNEL_REGEX = Regex("""chzzk\.naver\.com/([0-9a-f]{32})""")

    fun parseLive(url: String): String? = LIVE_REGEX.find(url)?.groupValues?.get(1)
    fun parseVideo(url: String): Long? = VIDEO_REGEX.find(url)?.groupValues?.get(1)?.toLongOrNull()
    fun parseClip(url: String): String? = CLIP_REGEX.find(url)?.groupValues?.get(1)
    fun parseChannel(url: String): String? = CHANNEL_REGEX.find(url)?.groupValues?.get(1)
}
