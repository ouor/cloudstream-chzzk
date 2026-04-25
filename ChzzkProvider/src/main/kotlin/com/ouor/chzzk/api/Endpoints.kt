package com.ouor.chzzk.api

object Endpoints {
    const val API_BASE = "https://api.chzzk.naver.com"
    const val WEB_BASE = "https://chzzk.naver.com"

    fun homeMain(slotSize: Int = 5) =
        "$API_BASE/service/v1/topics/HOME/sub-topics/HOME/main?slotSize=$slotSize"

    fun streamerPartners() = "$API_BASE/service/v1/streamer-partners/recommended"

    fun popularCategories(size: Int = 20) =
        "$API_BASE/service/v1/categories/live?size=$size"

    fun programSchedulesComing() =
        "$API_BASE/service/v1/program-schedules/coming"

    fun banners(deviceType: String = "PC", positionsIn: String = "HOME_SCHEDULE") =
        "$API_BASE/service/v1/banners?deviceType=$deviceType&positionsIn=$positionsIn"

    fun categoryLives(
        categoryType: String,
        categoryId: String,
        size: Int = 30,
        cursorConcurrentUserCount: Int? = null,
        cursorLiveId: Long? = null,
    ): String {
        val params = buildList {
            add("size=$size")
            if (cursorConcurrentUserCount != null) add("concurrentUserCount=$cursorConcurrentUserCount")
            if (cursorLiveId != null) add("liveId=$cursorLiveId")
        }.joinToString("&")
        return "$API_BASE/service/v2/categories/$categoryType/$categoryId/lives?$params"
    }

    fun searchChannels(keyword: String, offset: Int = 0, size: Int = 30) =
        "$API_BASE/service/v1/search/channels?keyword=${keyword.urlEncode()}&offset=$offset&size=$size&withFirstChannelContent=true"

    fun searchLives(keyword: String, offset: Int = 0, size: Int = 30) =
        "$API_BASE/service/v1/search/lives?keyword=${keyword.urlEncode()}&offset=$offset&size=$size"

    fun searchVideos(keyword: String, offset: Int = 0, size: Int = 30) =
        "$API_BASE/service/v1/search/videos?keyword=${keyword.urlEncode()}&offset=$offset&size=$size"

    fun searchAutoComplete(keyword: String, size: Int = 10) =
        "$API_BASE/service/v1/search/channels/auto-complete?keyword=${keyword.urlEncode()}&offset=0&size=$size"

    fun channel(channelId: String) = "$API_BASE/service/v1/channels/$channelId"

    fun channelVideos(channelId: String, page: Int = 0, size: Int = 30) =
        "$API_BASE/service/v1/channels/$channelId/videos" +
                "?sortType=LATEST&pagingType=PAGE&page=$page&size=$size&publishDateAt=&videoType="

    fun channelClips(
        channelId: String,
        orderType: String = "RECENT",
        size: Int = 50,
        cursorClipUID: String? = null,
    ) = "$API_BASE/service/v1/channels/$channelId/clips" +
            "?clipUID=${cursorClipUID.orEmpty()}&filterType=ALL&orderType=$orderType&size=$size"

    fun liveDetail(channelId: String, dt: String = randomDt()) =
        "$API_BASE/service/v3.3/channels/$channelId/live-detail?cu=false&dt=$dt&tm=true"

    fun videoDetail(videoNo: Long, dt: String = randomDt()) =
        "$API_BASE/service/v3/videos/$videoNo?dt=$dt"

    fun videoRewindAutoPlay(videoNo: Long) =
        "$API_BASE/service/v1/videos/$videoNo/live-rewind/auto-play-info"

    fun liveAutoPlay(liveId: Long) =
        "$API_BASE/service/v1/live/$liveId/auto-play-info"

    private fun randomDt(): String =
        (0..0xFFFFF).random().toString(16)

    private fun String.urlEncode(): String =
        java.net.URLEncoder.encode(this, "UTF-8")
}
