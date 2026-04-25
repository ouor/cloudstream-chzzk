package com.ouor.chzzk.loader

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.ouor.chzzk.PlayLink
import com.ouor.chzzk.api.ChzzkApi
import com.ouor.chzzk.api.Endpoints
import com.ouor.chzzk.api.augmentPlotWithCommunity
import com.ouor.chzzk.api.fetchAllChannelVideos
import com.ouor.chzzk.api.fetchLiveDetail
import com.ouor.chzzk.api.fetchLiveRecommendations
import com.ouor.chzzk.api.fetchPlayableVideoDetail
import com.ouor.chzzk.auth.ChzzkAuth
import com.ouor.chzzk.buildPlot
import com.ouor.chzzk.buildTags
import com.ouor.chzzk.buildVideoPlot
import com.ouor.chzzk.fillThumb
import com.ouor.chzzk.models.ChannelInfo
import com.ouor.chzzk.models.ChzzkResponse
import com.ouor.chzzk.models.ClipDetail
import com.ouor.chzzk.toMovieSearchResponse

internal suspend fun MainAPI.loadLive(url: String, channelId: String): LoadResponse {
    val detail = fetchLiveDetail(channelId)
    if (detail.status != "OPEN") {
        throw ErrorLoadingException("이 채널은 현재 방송 중이 아닙니다.")
    }
    if (detail.adult && !ChzzkAuth.current().isLoggedIn) {
        throw ErrorLoadingException("성인 방송입니다. 로그인 후 성인 인증된 계정으로 이용해주세요.")
    }
    if (detail.blindType != null) {
        throw ErrorLoadingException("이 방송은 시청이 제한되었습니다 (${detail.blindType}).")
    }
    val recs = runCatching { fetchLiveRecommendations(channelId) }.getOrDefault(emptyList())
    val basePlot = buildPlot(detail)
    val plotText = augmentPlotWithCommunity(
        base = if (detail.krOnlyViewing) "$basePlot\n\n🇰🇷 한국 지역에서만 시청 가능 (해외 IP 차단)" else basePlot,
        channelId = channelId,
    )
    return newLiveStreamLoadResponse(
        name = detail.liveTitle ?: detail.channel.channelName ?: "Chzzk Live",
        url = url,
        dataUrl = PlayLink(PlayLink.Kind.LIVE, channelId, detail.liveTitle).encode(),
    ) {
        posterUrl = detail.liveImageUrl?.let { fillThumb(it) } ?: detail.channel.channelImageUrl
        plot = plotText
        tags = buildTags(detail.liveCategoryValue, detail.tags)
        recommendations = recs
    }
}

internal suspend fun MainAPI.loadVideo(url: String, videoNo: Long): LoadResponse {
    val detail = fetchPlayableVideoDetail(videoNo)
    // Surface prev/next VOD navigation + same-channel recommendations.
    val nav = listOfNotNull(
        detail.prevVideo?.let { toMovieSearchResponse(it, detail.channel?.channelName, prefix = "← 이전: ") },
        detail.nextVideo?.let { toMovieSearchResponse(it, detail.channel?.channelName, prefix = "→ 다음: ") },
    )
    val channelRecs = detail.channel?.channelId?.let { id ->
        runCatching { fetchLiveRecommendations(id) }.getOrDefault(emptyList())
    }.orEmpty()
    return newMovieLoadResponse(
        name = detail.videoTitle ?: "video #$videoNo",
        url = url,
        type = TvType.Movie,
        dataUrl = PlayLink(PlayLink.Kind.VOD, videoNo.toString(), detail.videoTitle).encode(),
    ) {
        posterUrl = detail.thumbnailImageUrl
        plot = buildVideoPlot(detail)
        tags = buildTags(detail.videoCategoryValue, detail.tags)
        year = detail.publishDate?.take(4)?.toIntOrNull()
        duration = (detail.duration / 60L).toInt().takeIf { it > 0 }
        recommendations = nav + channelRecs
        // Chzzk pre-roll trailer is stitched into the VOD itself, so we
        // do not surface it as a separate LoadResponse.addTrailer entry —
        // the cloudstream stub does not expose a stable addTrailer API
        // for arbitrary mp4 URLs anyway.
    }
}

internal suspend fun MainAPI.loadClip(url: String, clipUID: String): LoadResponse {
    // Metadata comes from the official endpoint
    // GET /service/v1/clips/{clipUID}/detail (verified against the
    // April 2026 capture set). Playback URL is still resolved by
    // scraping the clip page in emitClipLinks since clipDetail only
    // carries `videoId` + `vodStatus`, not the actual m3u8 URL.
    val raw = ChzzkApi.getText(Endpoints.clipDetail(clipUID))
    val res = parseJson<ChzzkResponse<ClipDetail>>(raw)
    ChzzkApi.checkOk(res.code, res.message, "clip $clipUID")
    val detail = res.content
        ?: throw ErrorLoadingException("클립 정보를 불러오지 못했습니다 ($clipUID).")

    if (detail.adult && !ChzzkAuth.current().isLoggedIn) {
        throw ErrorLoadingException("성인 클립입니다. 로그인 후 성인 인증된 계정으로 이용해주세요.")
    }
    if (detail.blindType != null) {
        throw ErrorLoadingException("이 클립은 시청이 제한되었습니다 (${detail.blindType}).")
    }
    if (detail.vodStatus != null && detail.vodStatus != "ABR_HLS" && detail.vodStatus != "NONE") {
        throw ErrorLoadingException("재생할 수 없는 클립입니다 (vodStatus=${detail.vodStatus}).")
    }

    val owner = detail.optionalProperty?.ownerChannel
    val maker = detail.optionalProperty?.makerChannel
    val plotText = buildString {
        owner?.channelName?.let { append("채널: $it") }
        if (maker != null && maker.channelId != owner?.channelId && !maker.channelName.isNullOrBlank()) {
            if (isNotEmpty()) appendLine()
            append("클립 작성자: ${maker.channelName}")
        }
        if (!detail.clipCategory.isNullOrBlank()) {
            if (isNotEmpty()) appendLine()
            append("카테고리: ${detail.clipCategory}")
        }
        if (!detail.createdDate.isNullOrBlank()) {
            if (isNotEmpty()) appendLine()
            append("게시 ${detail.createdDate}")
        }
        if (detail.krOnlyViewing) {
            if (isNotEmpty()) appendLine()
            append("🇰🇷 한국 지역에서만 시청 가능 (해외 IP 차단)")
        }
    }
    // Carry videoId via PlayLink.extra so emitClipLinks can hit the
    // api-videohub play-info endpoint without re-fetching clipDetail.
    val videoId = detail.videoId
        ?: throw ErrorLoadingException("클립 videoId가 누락되었습니다 ($clipUID).")
    return newMovieLoadResponse(
        name = detail.clipTitle ?: "Chzzk Clip",
        url = url,
        type = TvType.Movie,
        dataUrl = PlayLink(PlayLink.Kind.CLIP, clipUID, detail.clipTitle, extra = videoId).encode(),
    ) {
        posterUrl = detail.thumbnailImageUrl ?: owner?.channelImageUrl
        plot = plotText.takeIf { it.isNotBlank() }
        tags = listOfNotNull(detail.clipCategory).filter { it.isNotBlank() }
        year = detail.createdDate?.take(4)?.toIntOrNull()
        duration = (detail.duration / 60L).toInt().takeIf { it > 0 }
    }
}

internal suspend fun MainAPI.loadChannel(url: String, channelId: String): LoadResponse {
    val rawChannel = ChzzkApi.getText(Endpoints.channel(channelId))
    val channelRes = parseJson<ChzzkResponse<ChannelInfo>>(rawChannel)
    ChzzkApi.checkOk(channelRes.code, channelRes.message, "channel $channelId")
    val info = channelRes.content ?: throw ErrorLoadingException("채널 정보를 불러오지 못했습니다.")

    val live = runCatching { fetchLiveDetail(channelId) }.getOrNull()?.takeIf { it.status == "OPEN" }
    // Hide blinded VODs and (for anonymous users) adult VODs so channel-page
    // taps don't dead-end on "링크를 찾을 수 없음". `vodStatus` (EXPIRED,
    // PROCESSING) is detail-only, so it stays guarded inside
    // [com.ouor.chzzk.loader.emitVodLinks] as a fallback. We deliberately
    // skip filtering on `exposure` — the channel-videos endpoint reports it
    // false for normal listings, so filtering on it hides everything.
    val isLoggedIn = ChzzkAuth.current().isLoggedIn
    val videos = runCatching { fetchAllChannelVideos(channelId) }
        .getOrDefault(emptyList())
        .filter { it.blindType == null && (!it.adult || isLoggedIn) }

    val episodes = mutableListOf<Episode>()
    // fix=false stops CloudStream from resolving Episode.data against
    // mainUrl. Without it, "VOD|12345|title|" gets rewritten to
    // "https://chzzk.naver.com/VOD|12345|title|" before reaching loadLinks,
    // which then fails PlayLink.decode and surfaces "링크를 찾을 수 없음".
    if (live != null) {
        episodes += newEpisode(
            url = PlayLink(PlayLink.Kind.LIVE, channelId, live.liveTitle).encode(),
            initializer = {
                name = "🔴 LIVE · ${live.liveTitle ?: "방송 중"}"
                posterUrl = live.liveImageUrl?.let { fillThumb(it) }
                description = "현재 ${live.concurrentUserCount}명 시청 중"
                episode = 0
            },
            fix = false,
        )
    }
    videos.forEachIndexed { index, video ->
        episodes += newEpisode(
            url = PlayLink(PlayLink.Kind.VOD, video.videoNo.toString(), video.videoTitle).encode(),
            initializer = {
                name = video.videoTitle
                posterUrl = video.thumbnailImageUrl
                description = video.publishDate
                episode = index + 1
            },
            fix = false,
        )
    }

    val recs = runCatching { fetchLiveRecommendations(channelId) }.getOrDefault(emptyList())
    val plotText = augmentPlotWithCommunity(info.channelDescription.orEmpty(), channelId)
    return newTvSeriesLoadResponse(
        name = info.channelName ?: channelId,
        url = url,
        type = TvType.TvSeries,
        episodes = episodes,
    ) {
        posterUrl = info.channelImageUrl
        plot = plotText
        tags = buildTags(live?.liveCategoryValue, live?.tags)
        recommendations = recs
    }
}
