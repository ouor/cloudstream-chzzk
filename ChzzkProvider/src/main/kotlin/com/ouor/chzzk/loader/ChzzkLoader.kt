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
import com.ouor.chzzk.api.fetchVideoDetail
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
    val detail = fetchVideoDetail(videoNo)
    // vodStatus values seen in captures:
    //   - "NONE"     — playable (used by /service/v3/videos/{n} for live-rewind VODs)
    //   - "ABR_HLS"  — playable (multi-bitrate HLS, used by clips and many VODs)
    // Anything else (e.g. "EXPIRED", "PROCESSING") means we cannot play.
    // Earlier this guard rejected "ABR_HLS" by mistake and produced a
    // user-visible "링크를 찾을 수 없음" toast.
    val playableStatuses = setOf("NONE", "ABR_HLS")
    if (detail.vodStatus != null && detail.vodStatus !in playableStatuses) {
        throw ErrorLoadingException("이 다시보기는 더 이상 시청할 수 없습니다 (${detail.vodStatus}).")
    }
    if (detail.adult && !ChzzkAuth.current().isLoggedIn) {
        throw ErrorLoadingException("성인 영상입니다. 로그인 후 성인 인증된 계정으로 이용해주세요.")
    }
    if (detail.blindType != null) {
        throw ErrorLoadingException("이 영상은 시청이 제한되었습니다 (${detail.blindType}).")
    }
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
    val videos = runCatching { fetchAllChannelVideos(channelId) }.getOrDefault(emptyList())

    val episodes = mutableListOf<Episode>()
    if (live != null) {
        episodes += newEpisode(
            data = PlayLink(PlayLink.Kind.LIVE, channelId, live.liveTitle).encode(),
        ) {
            name = "🔴 LIVE · ${live.liveTitle ?: "방송 중"}"
            posterUrl = live.liveImageUrl?.let { fillThumb(it) }
            description = "현재 ${live.concurrentUserCount}명 시청 중"
            episode = 0
        }
    }
    videos.forEachIndexed { index, video ->
        episodes += newEpisode(
            data = PlayLink(PlayLink.Kind.VOD, video.videoNo.toString(), video.videoTitle).encode(),
        ) {
            name = video.videoTitle
            posterUrl = video.thumbnailImageUrl
            description = video.publishDate
            episode = index + 1
        }
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
