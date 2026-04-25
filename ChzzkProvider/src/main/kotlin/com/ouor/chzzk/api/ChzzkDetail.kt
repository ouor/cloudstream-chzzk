package com.ouor.chzzk.api

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.ouor.chzzk.formatCurrency
import com.ouor.chzzk.models.CafeConnection
import com.ouor.chzzk.models.ChatRules
import com.ouor.chzzk.models.ChzzkResponse
import com.ouor.chzzk.models.DonationRankResponse
import com.ouor.chzzk.models.DonationRanker
import com.ouor.chzzk.models.LiveDetail
import com.ouor.chzzk.models.LiveRecommendedResponse
import com.ouor.chzzk.models.LogPowerWeekly
import com.ouor.chzzk.models.PageData
import com.ouor.chzzk.models.StreamerShopProducts
import com.ouor.chzzk.models.VideoDetail
import com.ouor.chzzk.models.VideoSummary
import com.ouor.chzzk.toSearchResponse

/** Hard cap on VODs collected per channel page (4 API pages of 30). */
internal const val MAX_CHANNEL_VIDEOS = 120

internal suspend fun fetchLiveDetail(channelId: String): LiveDetail {
    val raw = ChzzkApi.getText(Endpoints.liveDetail(channelId))
    val res = parseJson<ChzzkResponse<LiveDetail>>(raw)
    ChzzkApi.checkOk(res.code, res.message, "live-detail $channelId")
    return res.content ?: throw ErrorLoadingException("live-detail content 누락")
}

internal suspend fun fetchVideoDetail(videoNo: Long): VideoDetail {
    val raw = ChzzkApi.getText(Endpoints.videoDetail(videoNo))
    val res = parseJson<ChzzkResponse<VideoDetail>>(raw)
    ChzzkApi.checkOk(res.code, res.message, "video $videoNo")
    return res.content ?: throw ErrorLoadingException("video content 누락")
}

/**
 * Fetch the per-channel live recommendation strip (`/live-recommended`)
 * and flatten every recommended bucket into a single SearchResponse list.
 * Adult lives are filtered out so they are never recommended on a
 * non-authenticated client.
 */
internal suspend fun MainAPI.fetchLiveRecommendations(channelId: String): List<SearchResponse> {
    val raw = ChzzkApi.getText(Endpoints.channelLiveRecommended(channelId))
    val res = parseJson<ChzzkResponse<LiveRecommendedResponse>>(raw)
    if (res.code != 200) return emptyList()
    return res.content?.recommendedContents.orEmpty()
        .flatMap { it.lives }
        .filterNot { it.adult || it.channel.channelId == channelId }
        .distinctBy { it.liveId }
        .take(20)
        .map { toSearchResponse(it) }
}

/**
 * Walk the page-based VOD list for a channel and return up to
 * [MAX_CHANNEL_VIDEOS] entries. Stops early when the API reports the last
 * page or when the cap is reached. Each page is 30 items, so the default
 * cap of 120 means at most 4 round-trips for the largest channels.
 */
internal suspend fun fetchAllChannelVideos(channelId: String): List<VideoSummary> {
    val collected = mutableListOf<VideoSummary>()
    val pageSize = 30
    var page = 0
    while (collected.size < MAX_CHANNEL_VIDEOS) {
        val raw = ChzzkApi.getText(Endpoints.channelVideos(channelId, page = page, size = pageSize))
        val res = parseJson<ChzzkResponse<PageData<VideoSummary>>>(raw)
        ChzzkApi.checkOk(res.code, res.message, "channel videos $channelId p=$page")
        val data = res.content?.data.orEmpty()
        collected += data
        val totalPages = res.content?.totalPages ?: 0
        if (data.isEmpty() || page + 1 >= totalPages) break
        page++
    }
    return collected.take(MAX_CHANNEL_VIDEOS)
}

/**
 * Append weekly donation top-N, chat rules, café connection, and shop
 * info to a base plot. All fetches are best-effort — failures are
 * silently swallowed because the plot is metadata enrichment, not
 * core functionality.
 */
internal suspend fun augmentPlotWithCommunity(base: String, channelId: String): String {
    val rankers = runCatching {
        val raw = ChzzkApi.getText(Endpoints.donationRankWeekly(channelId, rankCount = 5))
        parseJson<ChzzkResponse<DonationRankResponse>>(raw)
            .content?.rankList.orEmpty()
    }.getOrDefault(emptyList<DonationRanker>())

    val rules = runCatching {
        val raw = ChzzkApi.getText(Endpoints.chatRules(channelId))
        parseJson<ChzzkResponse<ChatRules>>(raw).content
    }.getOrNull()

    val cafe = runCatching {
        val raw = ChzzkApi.getText(Endpoints.channelCafeConnection(channelId))
        parseJson<ChzzkResponse<CafeConnection>>(raw).content
    }.getOrNull()

    val logPower = runCatching {
        val raw = ChzzkApi.getText(Endpoints.logPowerWeekly(channelId))
        parseJson<ChzzkResponse<LogPowerWeekly>>(raw).content
    }.getOrNull()

    val shop = runCatching {
        val raw = ChzzkApi.getText(Endpoints.streamerShopProducts(channelId))
        parseJson<ChzzkResponse<StreamerShopProducts>>(raw).content
    }.getOrNull()

    return buildString {
        append(base)
        if (rankers.isNotEmpty()) {
            appendLine().appendLine()
            appendLine("💝 주간 후원 TOP")
            rankers.take(5).forEach { r ->
                val verified = if (r.verifiedMark) "✓ " else ""
                appendLine("${r.ranking}. $verified${r.nickName ?: "익명"} — ${formatCurrency(r.donationAmount)}")
            }
        }
        if (logPower != null && logPower.rankList.isNotEmpty()) {
            appendLine().appendLine()
            appendLine("⚡ 주간 로그파워 TOP")
            logPower.rankList.take(3).forEach { r ->
                appendLine("${r.ranking}. ${r.nickName ?: "익명"} — ${formatCurrency(r.logPower)} LP")
            }
        }
        if (cafe?.cafe != null && !cafe.cafe.name.isNullOrBlank()) {
            appendLine().appendLine()
            append("☕ 연결된 카페: ${cafe.cafe.name}")
        }
        if (shop != null && shop.totalCount > 0) {
            appendLine().appendLine()
            append("🛒 스트리머 상점: ${shop.totalCount}개 상품")
        }
        if (rules != null && !rules.rule.isNullOrBlank()) {
            appendLine().appendLine()
            appendLine("📋 채팅 규칙")
            append(rules.rule.lineSequence().take(4).joinToString("\n").take(400))
            if (rules.rule.length > 400) append("…")
        }
    }
}
