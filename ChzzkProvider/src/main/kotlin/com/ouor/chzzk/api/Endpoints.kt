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

    fun channelLiveRecommended(channelId: String) =
        "$API_BASE/service/v1/channels/$channelId/live-recommended"

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

    fun videoChats(videoNo: Long, playerMessageTime: Long = 0, previousVideoChatSize: Int = 50) =
        "$API_BASE/service/v1/videos/$videoNo/chats" +
                "?playerMessageTime=$playerMessageTime&previousVideoChatSize=$previousVideoChatSize"

    fun chatRules(channelId: String) =
        "$API_BASE/service/v1/channels/$channelId/chat-rules"

    fun donationRankWeekly(channelId: String, rankCount: Int = 5) =
        "$API_BASE/commercial/v1/channels/$channelId/donation/rank/weekly" +
                "?channelId=$channelId&withMyRank=false&rankCount=$rankCount"

    fun nicknameColorCodes() = "$API_BASE/service/v2/nickname/color/codes"

    fun channelCafeConnection(channelId: String) =
        "$API_BASE/service/v1/channels/$channelId/cafe-connection"

    fun logPowerWeekly(channelId: String) =
        "$API_BASE/service/v1/channels/$channelId/log-power/rank/weekly"

    fun streamerShopProducts(channelId: String, catalogType: String = "CATALOG") =
        "$API_BASE/commercial/v1/streamer-shop/$channelId/products?catalogType=$catalogType"

    fun clipDetail(clipUID: String) =
        "$API_BASE/service/v1/clips/$clipUID/detail" +
                "?optionalProperties=COMMENT&optionalProperties=PRIVATE_USER_BLOCK" +
                "&optionalProperties=PENALTY&optionalProperties=MAKER_CHANNEL" +
                "&optionalProperties=OWNER_CHANNEL"

    /**
     * The `dt` query parameter on /live-detail and /videos/{n} is a short
     * hex token that the official web client computes from page-load state.
     * Exact provenance has not been reverse-engineered (see PLAN.md §5).
     *
     * Captured samples are 4–5 lowercase hex chars (`245a2`, `2361d`,
     * `22498`). Empirically the server accepts any value matching this
     * shape, so we generate a 5-char lowercase hex token. If the server
     * ever begins to validate `dt` server-side, this is the only place
     * to update.
     */
    private fun randomDt(): String =
        "%05x".format((0x10000..0xFFFFF).random())

    private fun String.urlEncode(): String =
        java.net.URLEncoder.encode(this, "UTF-8")
}
