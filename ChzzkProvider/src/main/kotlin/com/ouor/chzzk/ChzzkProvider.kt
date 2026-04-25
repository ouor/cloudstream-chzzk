package com.ouor.chzzk

import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.ouor.chzzk.api.ChzzkApi
import com.ouor.chzzk.api.Endpoints
import com.ouor.chzzk.models.ChannelInfo
import com.ouor.chzzk.models.ChzzkResponse
import com.ouor.chzzk.models.HomeMain
import com.ouor.chzzk.models.LiveDetail
import com.ouor.chzzk.models.LivePlayback
import com.ouor.chzzk.models.LiveSummary
import com.ouor.chzzk.models.PageData
import com.ouor.chzzk.models.SearchChannelItem
import com.ouor.chzzk.models.StreamerPartnerList
import com.ouor.chzzk.models.VideoDetail
import com.ouor.chzzk.models.VideoSummary

class ChzzkProvider : MainAPI() {
    override var mainUrl = Urls.WEB_BASE
    override var name = "Chzzk"
    override val supportedTypes = setOf(TvType.Live, TvType.Movie)

    override var lang = "ko"

    override val hasMainPage = true
    override val hasQuickSearch = true

    override val mainPage = mainPageOf(
        SECTION_HOME to "인기 라이브",
        SECTION_PARTNERS to "파트너 스트리머",
        "GAME/League_of_Legends" to "리그 오브 레전드",
        "GAME/Teamfight_Tactics" to "전략적 팀 전투",
        "GAME/VALORANT" to "발로란트",
        "GAME/Overwatch_2" to "오버워치 2",
        "ETC/talk" to "잡담",
    )

    // --------------------------------------------------------------- mainPage

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items: List<SearchResponse> = when (val key = request.data) {
            SECTION_HOME -> loadHome()
            SECTION_PARTNERS -> loadPartners()
            else -> loadCategory(key)
        }
        return newHomePageResponse(
            list = HomePageList(request.name, items, isHorizontalImages = true),
            hasNext = false,
        )
    }

    private suspend fun loadHome(): List<SearchResponse> {
        val raw = ChzzkApi.get(Endpoints.homeMain()).text
        val res = parseJson<ChzzkResponse<HomeMain>>(raw)
        ChzzkApi.checkOk(res.code, res.message, "home main")
        val home = res.content ?: return emptyList()
        val seen = mutableSetOf<Long>()
        return buildList {
            home.topLives.filterNot { it.adult }.forEach { if (seen.add(it.liveId)) add(it.toSearchResponse()) }
            home.slots.flatMap { it.slotContents }.filterNot { it.adult }
                .forEach { if (seen.add(it.liveId)) add(it.toSearchResponse()) }
        }
    }

    private suspend fun loadPartners(): List<SearchResponse> {
        val raw = ChzzkApi.get(Endpoints.streamerPartners()).text
        val res = parseJson<ChzzkResponse<StreamerPartnerList>>(raw)
        ChzzkApi.checkOk(res.code, res.message, "streamer partners")
        return res.content?.streamerPartners.orEmpty().filter { it.openLive }.map { partner ->
            newLiveSearchResponse(
                name = partner.channelName ?: partner.originalNickname ?: partner.channelId,
                url = Urls.live(partner.channelId),
                type = TvType.Live,
            ) {
                posterUrl = partner.channelImageUrl
            }
        }
    }

    private suspend fun loadCategory(key: String): List<SearchResponse> {
        val parts = key.split("/", limit = 2)
        if (parts.size != 2) return emptyList()
        val (type, id) = parts
        val raw = ChzzkApi.get(Endpoints.categoryLives(type, id, size = 30)).text
        val res = parseJson<ChzzkResponse<PageData<LiveSummary>>>(raw)
        ChzzkApi.checkOk(res.code, res.message, "category lives $key")
        return res.content?.data.orEmpty().filterNot { it.adult }.map { it.toSearchResponse() }
    }

    // ----------------------------------------------------------------- search

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun search(query: String): List<SearchResponse> {
        if (query.isBlank()) return emptyList()
        val results = mutableListOf<SearchResponse>()
        val seenChannels = mutableSetOf<String>()
        val seenVideos = mutableSetOf<Long>()

        runCatching {
            val raw = ChzzkApi.get(Endpoints.searchChannels(query)).text
            val res = tryParseJson<ChzzkResponse<PageData<SearchChannelItem>>>(raw)
            res?.content?.data.orEmpty().forEach { item ->
                if (!seenChannels.add(item.channel.channelId)) return@forEach
                val live = item.content?.live
                if (live != null && live.status == "OPEN") {
                    results += newLiveSearchResponse(
                        name = "${item.channel.channelName ?: item.channel.channelId} · ${live.liveTitle ?: ""}".trim(),
                        url = Urls.live(item.channel.channelId),
                        type = TvType.Live,
                    ) {
                        posterUrl = live.liveImageUrl?.let { fillThumb(it) } ?: item.channel.channelImageUrl
                    }
                } else {
                    results += newLiveSearchResponse(
                        name = item.channel.channelName ?: item.channel.channelId,
                        url = Urls.channel(item.channel.channelId),
                        type = TvType.Live,
                    ) {
                        posterUrl = item.channel.channelImageUrl
                    }
                }
                item.content?.videos.orEmpty().take(3).forEach { video ->
                    if (seenVideos.add(video.videoNo)) {
                        results += video.toMovieSearchResponse(item.channel.channelName)
                    }
                }
            }
        }

        runCatching {
            val raw = ChzzkApi.get(Endpoints.searchLives(query)).text
            val res = tryParseJson<ChzzkResponse<PageData<LiveSummary>>>(raw)
            res?.content?.data.orEmpty().forEach { live ->
                if (seenChannels.add(live.channel.channelId)) results += live.toSearchResponse()
            }
        }

        runCatching {
            val raw = ChzzkApi.get(Endpoints.searchVideos(query)).text
            val res = tryParseJson<ChzzkResponse<PageData<VideoSummary>>>(raw)
            res?.content?.data.orEmpty().forEach { video ->
                if (seenVideos.add(video.videoNo)) {
                    results += video.toMovieSearchResponse(video.channel?.channelName)
                }
            }
        }

        return results
    }

    // ------------------------------------------------------------------- load

    override suspend fun load(url: String): LoadResponse? = when {
        Urls.parseLive(url) != null -> loadLive(url, Urls.parseLive(url)!!)
        Urls.parseVideo(url) != null -> loadVideo(url, Urls.parseVideo(url)!!)
        Urls.parseClip(url) != null -> loadClip(url, Urls.parseClip(url)!!)
        Urls.parseChannel(url) != null -> loadChannel(url, Urls.parseChannel(url)!!)
        else -> null
    }

    private suspend fun loadLive(url: String, channelId: String): LoadResponse {
        val detail = fetchLiveDetail(channelId)
        if (detail.status != "OPEN") {
            throw ErrorLoadingException("이 채널은 현재 방송 중이 아닙니다.")
        }
        if (detail.adult) {
            throw ErrorLoadingException("성인 인증이 필요한 방송입니다. (로그인 미지원)")
        }
        if (detail.blindType != null) {
            throw ErrorLoadingException("이 방송은 시청이 제한되었습니다 (${detail.blindType}).")
        }
        return newLiveStreamLoadResponse(
            name = detail.liveTitle ?: detail.channel.channelName ?: "Chzzk Live",
            url = url,
            dataUrl = encodePlayLink(PlayLink(PlayLink.Kind.LIVE, channelId, detail.liveTitle)),
        ) {
            posterUrl = detail.liveImageUrl?.let { fillThumb(it) } ?: detail.channel.channelImageUrl
            plot = buildPlot(detail)
            tags = buildTags(detail.liveCategoryValue, detail.tags)
        }
    }

    private suspend fun loadVideo(url: String, videoNo: Long): LoadResponse {
        val detail = fetchVideoDetail(videoNo)
        if (detail.vodStatus != null && detail.vodStatus != "NONE") {
            throw ErrorLoadingException("이 다시보기는 더 이상 시청할 수 없습니다 (${detail.vodStatus}).")
        }
        if (detail.adult) {
            throw ErrorLoadingException("성인 인증이 필요한 영상입니다. (로그인 미지원)")
        }
        if (detail.blindType != null) {
            throw ErrorLoadingException("이 영상은 시청이 제한되었습니다 (${detail.blindType}).")
        }
        return newMovieLoadResponse(
            name = detail.videoTitle ?: "video #$videoNo",
            url = url,
            type = TvType.Movie,
            dataUrl = encodePlayLink(PlayLink(PlayLink.Kind.VOD, videoNo.toString(), detail.videoTitle)),
        ) {
            posterUrl = detail.thumbnailImageUrl
            plot = buildVideoPlot(detail)
            tags = buildTags(detail.videoCategoryValue, detail.tags)
            year = detail.publishDate?.take(4)?.toIntOrNull()
            duration = (detail.duration / 60L).toInt().takeIf { it > 0 }
        }
    }

    private suspend fun loadClip(url: String, clipUID: String): LoadResponse {
        // Clip playback endpoint not yet covered by the captured spec — see
        // PLAN.md §12. Surface a friendly error so the entry is at least
        // routable from search results.
        throw ErrorLoadingException("클립 재생은 아직 지원되지 않습니다 ($clipUID).")
    }

    private suspend fun loadChannel(url: String, channelId: String): LoadResponse {
        val rawChannel = ChzzkApi.get(Endpoints.channel(channelId)).text
        val channelRes = parseJson<ChzzkResponse<ChannelInfo>>(rawChannel)
        ChzzkApi.checkOk(channelRes.code, channelRes.message, "channel $channelId")
        val info = channelRes.content ?: throw ErrorLoadingException("채널 정보를 불러오지 못했습니다.")

        val live = runCatching { fetchLiveDetail(channelId) }.getOrNull()?.takeIf { it.status == "OPEN" }
        val videos = runCatching {
            val raw = ChzzkApi.get(Endpoints.channelVideos(channelId, page = 0, size = 30)).text
            val res = parseJson<ChzzkResponse<PageData<VideoSummary>>>(raw)
            res.content?.data.orEmpty()
        }.getOrDefault(emptyList())

        val episodes = mutableListOf<Episode>()
        if (live != null) {
            episodes += newEpisode(
                data = encodePlayLink(PlayLink(PlayLink.Kind.LIVE, channelId, live.liveTitle)),
            ) {
                name = "🔴 LIVE · ${live.liveTitle ?: "방송 중"}"
                posterUrl = live.liveImageUrl?.let { fillThumb(it) }
                description = "현재 ${live.concurrentUserCount}명 시청 중"
                episode = 0
            }
        }
        videos.forEachIndexed { index, video ->
            episodes += newEpisode(
                data = encodePlayLink(PlayLink(PlayLink.Kind.VOD, video.videoNo.toString(), video.videoTitle)),
            ) {
                name = video.videoTitle
                posterUrl = video.thumbnailImageUrl
                description = video.publishDate
                episode = index + 1
            }
        }

        return newTvSeriesLoadResponse(
            name = info.channelName ?: channelId,
            url = url,
            type = TvType.TvSeries,
            episodes = episodes,
        ) {
            posterUrl = info.channelImageUrl
            plot = info.channelDescription
            tags = buildTags(live?.liveCategoryValue, live?.tags)
        }
    }

    // -------------------------------------------------------------- loadLinks

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val link = decodePlayLink(data) ?: return false
        return when (link.kind) {
            PlayLink.Kind.LIVE -> emitLiveLinks(link.id, link.title, callback)
            PlayLink.Kind.VOD -> emitVodLinks(link.id.toLong(), link.title, callback)
        }
    }

    private suspend fun emitLiveLinks(
        channelId: String,
        title: String?,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val detail = fetchLiveDetail(channelId)
        val playbackJson = detail.livePlaybackJson
            ?: throw ErrorLoadingException("livePlaybackJson 누락")
        val playback = parseJson<LivePlayback>(playbackJson)
        return emitMediaLinks(
            mediaList = playback.media,
            label = title ?: detail.liveTitle ?: detail.channel.channelName ?: "Chzzk Live",
            isLive = true,
            callback = callback,
        )
    }

    private suspend fun emitVodLinks(
        videoNo: Long,
        title: String?,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val detail = fetchVideoDetail(videoNo)
        val playbackJson = detail.liveRewindPlaybackJson
            ?: throw ErrorLoadingException("liveRewindPlaybackJson 누락 (vodStatus=${detail.vodStatus})")
        val playback = parseJson<LivePlayback>(playbackJson)
        return emitMediaLinks(
            mediaList = playback.media,
            label = title ?: detail.videoTitle ?: "video #$videoNo",
            isLive = false,
            callback = callback,
        )
    }

    private suspend fun emitMediaLinks(
        mediaList: List<com.ouor.chzzk.models.PlaybackMedia>,
        label: String,
        isLive: Boolean,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        // Prefer the standard HLS master playlist; fall back to LLHLS only when
        // HLS is unavailable, since some players struggle with low-latency HLS.
        val ordered = mediaList.sortedBy {
            when (it.mediaId) {
                "HLS" -> 0
                "LLHLS" -> 1
                else -> 2
            }
        }
        var emitted = 0
        for (media in ordered) {
            if (media.protocol?.uppercase() != "HLS") continue
            // Expand the master m3u8 into per-quality variants.
            val expanded = runCatching {
                M3u8Helper.generateM3u8(
                    source = name,
                    streamUrl = media.path,
                    referer = Urls.WEB_BASE,
                ).also { variants -> variants.forEach { callback(it) } }
            }.getOrNull()

            if (expanded.isNullOrEmpty()) {
                callback(
                    ExtractorLink(
                        source = name,
                        name = "$name ${media.mediaId} ($label)",
                        url = media.path,
                        referer = Urls.WEB_BASE,
                        quality = qualityFromTracks(media.encodingTrack),
                        isM3u8 = true,
                        type = ExtractorLinkType.M3U8,
                    )
                )
            }
            emitted++
            if (isLive) break // one master is enough for live
        }
        return emitted > 0
    }

    // ----------------------------------------------------------------- helpers

    private suspend fun fetchLiveDetail(channelId: String): LiveDetail {
        val raw = ChzzkApi.get(Endpoints.liveDetail(channelId)).text
        val res = parseJson<ChzzkResponse<LiveDetail>>(raw)
        ChzzkApi.checkOk(res.code, res.message, "live-detail $channelId")
        return res.content ?: throw ErrorLoadingException("live-detail content 누락")
    }

    private suspend fun fetchVideoDetail(videoNo: Long): VideoDetail {
        val raw = ChzzkApi.get(Endpoints.videoDetail(videoNo)).text
        val res = parseJson<ChzzkResponse<VideoDetail>>(raw)
        ChzzkApi.checkOk(res.code, res.message, "video $videoNo")
        return res.content ?: throw ErrorLoadingException("video content 누락")
    }

    private fun encodePlayLink(link: PlayLink): String =
        "${link.kind.name}|${link.id}|${link.title?.replace("|", "／") ?: ""}"

    private fun decodePlayLink(data: String): PlayLink? {
        val parts = data.split("|", limit = 3)
        if (parts.size < 2) return null
        val kind = runCatching { PlayLink.Kind.valueOf(parts[0]) }.getOrNull() ?: return null
        return PlayLink(kind = kind, id = parts[1], title = parts.getOrNull(2)?.takeIf { it.isNotBlank() })
    }

    private fun LiveSummary.toSearchResponse(): LiveSearchResponse {
        val verified = if (channel.verifiedMark) "✓ " else ""
        val viewers = if (concurrentUserCount > 0) " · ${formatViewers(concurrentUserCount)}" else ""
        val title = "$verified${channel.channelName ?: channel.channelId}$viewers"
        return newLiveSearchResponse(
            name = if (liveTitle.isNullOrBlank()) title else "$title · $liveTitle",
            url = Urls.live(channel.channelId),
            type = TvType.Live,
        ) {
            posterUrl = liveImageUrl?.let { fillThumb(it) } ?: defaultThumbnailImageUrl ?: channel.channelImageUrl
        }
    }

    private fun formatViewers(count: Int): String = when {
        count >= 10000 -> "%.1f만명".format(count / 10000.0)
        count >= 1000 -> "${count / 1000}.${(count % 1000) / 100}천명"
        else -> "${count}명"
    }

    private fun VideoSummary.toMovieSearchResponse(channelName: String?): MovieSearchResponse {
        val title = listOfNotNull(channelName, videoTitle).joinToString(" · ").ifBlank { "video #$videoNo" }
        return newMovieSearchResponse(
            name = title,
            url = Urls.video(videoNo),
            type = TvType.Movie,
        ) {
            posterUrl = thumbnailImageUrl
        }
    }

    private fun fillThumb(template: String, type: String = "720"): String =
        template.replace("{type}", type)

    private fun buildPlot(detail: LiveDetail): String = buildString {
        append(detail.channel.channelName ?: detail.channel.channelId)
        if (detail.liveCategoryValue != null) append(" · ${detail.liveCategoryValue}")
        appendLine()
        append("동시 시청자 ${detail.concurrentUserCount}명")
        if (!detail.openDate.isNullOrBlank()) {
            appendLine()
            append("방송 시작 ${detail.openDate}")
        }
    }

    private fun buildVideoPlot(detail: VideoDetail): String = buildString {
        if (detail.channel?.channelName != null) appendLine(detail.channel.channelName)
        if (detail.videoCategoryValue != null) appendLine("카테고리: ${detail.videoCategoryValue}")
        if (!detail.publishDate.isNullOrBlank()) appendLine("게시 ${detail.publishDate}")
        append("조회 ${detail.readCount}회")
    }

    private fun buildTags(category: String?, tags: List<String>?): List<String> {
        val out = mutableListOf<String>()
        if (!category.isNullOrBlank()) out += category
        if (tags != null) out += tags
        return out.distinct()
    }

    private fun qualityFromTracks(tracks: List<com.ouor.chzzk.models.EncodingTrack>): Int {
        // Heuristic: report the master's max height; CloudStream uses Quality
        // ints aligned to vertical resolution.
        return tracks.mapNotNull { it.videoHeight }.maxOrNull() ?: 0
    }

    companion object {
        private const val SECTION_HOME = "__HOME__"
        private const val SECTION_PARTNERS = "__PARTNERS__"
    }
}
