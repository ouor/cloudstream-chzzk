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
import com.ouor.chzzk.auth.ChzzkAuth
import com.ouor.chzzk.models.CafeConnection
import com.ouor.chzzk.models.ChatRules
import com.ouor.chzzk.models.ClipDetail
import com.ouor.chzzk.models.DonationRankResponse
import com.ouor.chzzk.models.DonationRanker
import com.ouor.chzzk.models.LogPowerWeekly
import com.ouor.chzzk.models.StreamerShopProducts
import com.ouor.chzzk.models.ChannelInfo
import com.ouor.chzzk.models.ChzzkResponse
import com.ouor.chzzk.models.HomeMain
import com.ouor.chzzk.models.LiveDetail
import com.ouor.chzzk.models.LivePlayback
import com.ouor.chzzk.models.LiveRecommendedResponse
import com.ouor.chzzk.models.LiveSummary
import com.ouor.chzzk.models.PageData
import com.ouor.chzzk.models.ProgramSchedule
import com.ouor.chzzk.models.ProgramScheduleList
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
        SECTION_SCHEDULE to "📅 방송 예정",
        "GAME/League_of_Legends" to "리그 오브 레전드",
        "GAME/Teamfight_Tactics" to "전략적 팀 전투",
        "GAME/VALORANT" to "발로란트",
        "GAME/Overwatch_2" to "오버워치 2",
        "GAME/StarCraft" to "스타크래프트",
        "GAME/Minecraft" to "마인크래프트",
        "GAME/PUBG_BATTLEGROUNDS" to "배틀그라운드",
        "ETC/talk" to "잡담",
        "ETC/sports" to "스포츠",
        "ETC/music" to "음악",
    )

    // --------------------------------------------------------------- mainPage

    /**
     * Per-section cache of cursors discovered while paginating. Key is the
     * section identifier (e.g. `GAME/League_of_Legends`); value maps page
     * index → next-page query string returned by the API. The framework calls
     * getMainPage sequentially as the user scrolls, so this lets us hop
     * directly to page N without rewalking pages 1..N-1.
     */
    private val categoryCursorCache: MutableMap<String, MutableMap<Int, String>> = mutableMapOf()

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val key = request.data
        val items: List<SearchResponse>
        val hasNext: Boolean
        when (key) {
            SECTION_HOME -> {
                items = loadHome()
                hasNext = false
            }
            SECTION_PARTNERS -> {
                items = loadPartners()
                hasNext = false
            }
            SECTION_SCHEDULE -> {
                items = loadSchedule()
                hasNext = false
            }
            else -> {
                val (loaded, more) = loadCategoryPage(key, page)
                items = loaded
                hasNext = more
            }
        }
        return newHomePageResponse(
            list = HomePageList(request.name, items, isHorizontalImages = true),
            hasNext = hasNext,
        )
    }

    private suspend fun loadHome(): List<SearchResponse> {
        val raw = ChzzkApi.getText(Endpoints.homeMain())
        val res = parseJson<ChzzkResponse<HomeMain>>(raw)
        ChzzkApi.checkOk(res.code, res.message, "home main")
        val home = res.content ?: return emptyList()
        val hideAdult = !ChzzkAuth.current().isLoggedIn
        val seen = mutableSetOf<Long>()
        return buildList {
            home.topLives.asSequence()
                .filterNot { hideAdult && it.adult }
                .forEach { if (seen.add(it.liveId)) add(it.toSearchResponse()) }
            home.slots.asSequence().flatMap { it.slotContents.asSequence() }
                .filterNot { hideAdult && it.adult }
                .forEach { if (seen.add(it.liveId)) add(it.toSearchResponse()) }
        }
    }

    private suspend fun loadSchedule(): List<SearchResponse> {
        val raw = ChzzkApi.getText(Endpoints.programSchedulesComing())
        val res = parseJson<ChzzkResponse<ProgramScheduleList>>(raw)
        ChzzkApi.checkOk(res.code, res.message, "program-schedules")
        return res.content?.programSchedules.orEmpty().map { it.toSearchResponse() }
    }

    private fun ProgramSchedule.toSearchResponse(): LiveSearchResponse {
        val ch = channel
        val title = listOfNotNull(scheduleDate, scheduleTitle).joinToString(" · ")
        val name = listOfNotNull(ch?.channelName, title).joinToString(" — ")
            .ifBlank { scheduleTitle ?: "방송 예정" }
        val targetUrl = if (ch != null) Urls.channel(ch.channelId) else Urls.WEB_BASE
        return newLiveSearchResponse(
            name = name,
            url = targetUrl,
            type = TvType.Live,
        ) {
            posterUrl = ch?.channelImageUrl
        }
    }

    private suspend fun loadPartners(): List<SearchResponse> {
        val raw = ChzzkApi.getText(Endpoints.streamerPartners())
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

    /**
     * Walk the cursor chain for a category up to [page] (1-indexed) and return
     * the lives discovered on that page plus a flag indicating whether the
     * API has further pages. Cursors discovered along the way are cached so
     * that a subsequent `page+1` call only fires a single request.
     */
    private suspend fun loadCategoryPage(key: String, page: Int): Pair<List<SearchResponse>, Boolean> {
        val parts = key.split("/", limit = 2)
        if (parts.size != 2) return emptyList<SearchResponse>() to false
        val (type, id) = parts

        val cache = categoryCursorCache.getOrPut(key) { mutableMapOf() }
        // Cursor for fetching `page` is whatever the API returned at the end
        // of `page - 1`. Page 1 has no cursor.
        var cursorConcurrent: Int? = null
        var cursorLiveId: Long? = null

        // Re-walk until we know the cursor for the target page.
        var current = 1
        while (current < page) {
            val cached = cache[current]
            if (cached != null) {
                val (c, l) = decodeCategoryCursor(cached)
                cursorConcurrent = c
                cursorLiveId = l
                current++
                continue
            }
            // No cached cursor for `current` page — fetch it just to advance.
            val intermediate = fetchCategory(type, id, cursorConcurrent, cursorLiveId)
            val next = intermediate.next
            if (next == null) {
                // Stream ran dry before we reached `page`.
                return emptyList<SearchResponse>() to false
            }
            cache[current] = encodeCategoryCursor(next.first, next.second)
            cursorConcurrent = next.first
            cursorLiveId = next.second
            current++
        }

        val batch = fetchCategory(type, id, cursorConcurrent, cursorLiveId)
        val next = batch.next
        if (next != null) {
            cache[page] = encodeCategoryCursor(next.first, next.second)
        }
        // adult filtering already applied in fetchCategory based on auth state
        val items = batch.lives.map { it.toSearchResponse() }
        return items to (next != null)
    }

    private suspend fun fetchCategory(
        type: String,
        id: String,
        cursorConcurrentUserCount: Int?,
        cursorLiveId: Long?,
    ): CategoryPage {
        val raw = ChzzkApi.getText(
            Endpoints.categoryLives(
                categoryType = type,
                categoryId = id,
                size = 30,
                cursorConcurrentUserCount = cursorConcurrentUserCount,
                cursorLiveId = cursorLiveId,
            )
        )
        val res = parseJson<ChzzkResponse<PageData<LiveSummary>>>(raw)
        ChzzkApi.checkOk(res.code, res.message, "category lives $type/$id")
        val pageData = res.content
        val hideAdult = !ChzzkAuth.current().isLoggedIn
        val lives = pageData?.data.orEmpty().filterNot { hideAdult && it.adult }
        val nextMap = pageData?.page?.next
        val next = nextMap?.let {
            val cu = (it["concurrentUserCount"] as? Number)?.toInt()
            val li = (it["liveId"] as? Number)?.toLong()
            if (cu != null && li != null) cu to li else null
        }
        return CategoryPage(lives, next)
    }

    private data class CategoryPage(val lives: List<LiveSummary>, val next: Pair<Int, Long>?)

    private fun encodeCategoryCursor(concurrent: Int, liveId: Long): String =
        "$concurrent|$liveId"

    private fun decodeCategoryCursor(s: String): Pair<Int?, Long?> {
        val parts = s.split("|", limit = 2)
        return parts.getOrNull(0)?.toIntOrNull() to parts.getOrNull(1)?.toLongOrNull()
    }

    // ----------------------------------------------------------------- search

    /**
     * Lightweight autocomplete for the search bar. The full [search]
     * fan-outs to three endpoints (channels + lives + videos), which is
     * overkill for keystroke-by-keystroke responsiveness. Here we hit
     * /search/channels/auto-complete which returns just a `data: string[]`
     * of channel names matching the prefix, then upgrade each to a single
     * channel-search call to fetch the channelId/avatar. This keeps the
     * dropdown snappy while still landing on a usable card.
     */
    override suspend fun quickSearch(query: String): List<SearchResponse> {
        if (query.isBlank() || query.startsWith("#")) return search(query)
        val rawNames = runCatching {
            ChzzkApi.getText(Endpoints.searchAutoComplete(query, size = 10))
        }.getOrNull() ?: return search(query)
        val names = tryParseJson<ChzzkResponse<PageData<String>>>(rawNames)
            ?.content?.data
            .orEmpty()
            .filter { it.isNotBlank() }
            .take(8)
        if (names.isEmpty()) return search(query)

        // Resolve each name to a real channel (parallel via async would be
        // ideal — keep it sequential to avoid blowing past Chzzk rate limits
        // and to keep failures isolated).
        val results = mutableListOf<SearchResponse>()
        val seen = mutableSetOf<String>()
        for (name in names) {
            val raw = runCatching { ChzzkApi.getText(Endpoints.searchChannels(name, size = 1)) }.getOrNull()
                ?: continue
            val res = tryParseJson<ChzzkResponse<PageData<SearchChannelItem>>>(raw)
            val item = res?.content?.data?.firstOrNull() ?: continue
            if (!seen.add(item.channel.channelId)) continue
            val live = item.content?.live?.takeIf { it.status == "OPEN" }
            results += if (live != null) {
                newLiveSearchResponse(
                    name = "${item.channel.channelName ?: item.channel.channelId} · ${live.liveTitle ?: ""}".trim(),
                    url = Urls.live(item.channel.channelId),
                    type = TvType.Live,
                ) {
                    posterUrl = live.liveImageUrl?.let { fillThumb(it) } ?: item.channel.channelImageUrl
                }
            } else {
                newLiveSearchResponse(
                    name = item.channel.channelName ?: item.channel.channelId,
                    url = Urls.channel(item.channel.channelId),
                    type = TvType.Live,
                ) {
                    posterUrl = item.channel.channelImageUrl
                }
            }
        }
        return results
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (query.isBlank()) return emptyList()

        // Tag search: a leading `#` switches to tag-membership filtering — the
        // base API has no dedicated tag endpoint, so we fan out the same query
        // (sans `#`) and post-filter results whose tags[] contains the term.
        val isTagSearch = query.startsWith("#")
        // Type filter prefixes: `live:foo`, `vod:foo`, `clip:foo` restrict the
        // fan-out to a single endpoint, halving load time when the user knows
        // what they want.
        val (typeFilter, queryAfterType) = parseSearchTypeFilter(query)
        val effectiveQuery = (if (isTagSearch) queryAfterType.removePrefix("#") else queryAfterType).trim()
        if (effectiveQuery.isBlank()) return emptyList()
        val tagFilter: ((List<String>?) -> Boolean) = if (isTagSearch) {
            { tags -> tags?.any { it.equals(effectiveQuery, ignoreCase = true) } == true }
        } else {
            { _ -> true }
        }

        val results = mutableListOf<SearchResponse>()
        val seenChannels = mutableSetOf<String>()
        val seenVideos = mutableSetOf<Long>()

        if (typeFilter == null || typeFilter == SearchType.CHANNEL || typeFilter == SearchType.LIVE) {
            runCatching {
                val raw = ChzzkApi.getText(Endpoints.searchChannels(effectiveQuery))
                val res = tryParseJson<ChzzkResponse<PageData<SearchChannelItem>>>(raw)
                res?.content?.data.orEmpty().forEach { item ->
                    if (!seenChannels.add(item.channel.channelId)) return@forEach
                    val live = item.content?.live
                    if (live != null && live.status == "OPEN" && tagFilter(live.tags)) {
                        results += newLiveSearchResponse(
                            name = "${item.channel.channelName ?: item.channel.channelId} · ${live.liveTitle ?: ""}".trim(),
                            url = Urls.live(item.channel.channelId),
                            type = TvType.Live,
                        ) {
                            posterUrl = live.liveImageUrl?.let { fillThumb(it) } ?: item.channel.channelImageUrl
                        }
                    } else if (!isTagSearch && typeFilter != SearchType.LIVE) {
                        results += newLiveSearchResponse(
                            name = item.channel.channelName ?: item.channel.channelId,
                            url = Urls.channel(item.channel.channelId),
                            type = TvType.Live,
                        ) {
                            posterUrl = item.channel.channelImageUrl
                        }
                    }
                    if (typeFilter != SearchType.LIVE) {
                        item.content?.videos.orEmpty().take(3).forEach { video ->
                            if (!tagFilter(video.tags)) return@forEach
                            if (seenVideos.add(video.videoNo)) {
                                results += video.toMovieSearchResponse(item.channel.channelName)
                            }
                        }
                    }
                }
            }
        }

        if (typeFilter == null || typeFilter == SearchType.LIVE) {
            runCatching {
                val raw = ChzzkApi.getText(Endpoints.searchLives(effectiveQuery))
                val res = tryParseJson<ChzzkResponse<PageData<LiveSummary>>>(raw)
                res?.content?.data.orEmpty().forEach { live ->
                    if (!tagFilter(live.tags)) return@forEach
                    if (seenChannels.add(live.channel.channelId)) results += live.toSearchResponse()
                }
            }
        }

        if (typeFilter == null || typeFilter == SearchType.VOD) {
            runCatching {
                val raw = ChzzkApi.getText(Endpoints.searchVideos(effectiveQuery))
                val res = tryParseJson<ChzzkResponse<PageData<VideoSummary>>>(raw)
                res?.content?.data.orEmpty().forEach { video ->
                    if (!tagFilter(video.tags)) return@forEach
                    if (seenVideos.add(video.videoNo)) {
                        results += video.toMovieSearchResponse(video.channel?.channelName)
                    }
                }
            }
        }

        return results
    }

    private enum class SearchType { LIVE, VOD, CHANNEL }

    /**
     * Strip a leading `live:` / `vod:` / `clip:` / `channel:` prefix and
     * return the remaining query plus its inferred type filter.
     */
    private fun parseSearchTypeFilter(raw: String): Pair<SearchType?, String> {
        val lowered = raw.trim()
        for (prefix in TYPE_PREFIXES) {
            if (lowered.startsWith(prefix.first, ignoreCase = true)) {
                return prefix.second to lowered.removePrefix(prefix.first).removePrefix(" ")
            }
        }
        return null to raw
    }

    private val TYPE_PREFIXES = listOf(
        "live:" to SearchType.LIVE,
        "vod:" to SearchType.VOD,
        "video:" to SearchType.VOD,
        "channel:" to SearchType.CHANNEL,
    )

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
            dataUrl = encodePlayLink(PlayLink(PlayLink.Kind.LIVE, channelId, detail.liveTitle)),
        ) {
            posterUrl = detail.liveImageUrl?.let { fillThumb(it) } ?: detail.channel.channelImageUrl
            plot = plotText
            tags = buildTags(detail.liveCategoryValue, detail.tags)
            recommendations = recs
        }
    }

    private suspend fun loadVideo(url: String, videoNo: Long): LoadResponse {
        val detail = fetchVideoDetail(videoNo)
        if (detail.vodStatus != null && detail.vodStatus != "NONE") {
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
            detail.prevVideo?.toMovieSearchResponse(detail.channel?.channelName, prefix = "← 이전: "),
            detail.nextVideo?.toMovieSearchResponse(detail.channel?.channelName, prefix = "→ 다음: "),
        )
        val channelRecs = detail.channel?.channelId?.let { id ->
            runCatching { fetchLiveRecommendations(id) }.getOrDefault(emptyList())
        }.orEmpty()
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
            recommendations = nav + channelRecs
            // Chzzk pre-roll trailer is stitched into the VOD itself, so we
            // do not surface it as a separate LoadResponse.addTrailer entry —
            // the cloudstream stub does not expose a stable addTrailer API
            // for arbitrary mp4 URLs anyway.
        }
    }

    private suspend fun loadClip(url: String, clipUID: String): LoadResponse {
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
        return newMovieLoadResponse(
            name = detail.clipTitle ?: "Chzzk Clip",
            url = url,
            type = TvType.Movie,
            dataUrl = encodePlayLink(PlayLink(PlayLink.Kind.CLIP, clipUID, detail.clipTitle)),
        ) {
            posterUrl = detail.thumbnailImageUrl ?: owner?.channelImageUrl
            plot = plotText.takeIf { it.isNotBlank() }
            tags = listOfNotNull(detail.clipCategory).filter { it.isNotBlank() }
            year = detail.createdDate?.take(4)?.toIntOrNull()
            duration = (detail.duration / 60L).toInt().takeIf { it > 0 }
        }
    }

    private suspend fun loadChannel(url: String, channelId: String): LoadResponse {
        val rawChannel = ChzzkApi.getText(Endpoints.channel(channelId))
        val channelRes = parseJson<ChzzkResponse<ChannelInfo>>(rawChannel)
        ChzzkApi.checkOk(channelRes.code, channelRes.message, "channel $channelId")
        val info = channelRes.content ?: throw ErrorLoadingException("채널 정보를 불러오지 못했습니다.")

        val live = runCatching { fetchLiveDetail(channelId) }.getOrNull()?.takeIf { it.status == "OPEN" }
        val videos = runCatching { fetchAllChannelVideos(channelId) }.getOrDefault(emptyList())

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

    // -------------------------------------------------------------- loadLinks
    //
    // Chzzk's `playerAdFlag` (preRoll/midRoll) governs ad insertion in the
    // official web player. We deliberately ignore it here — we pull the HLS
    // master directly, which the CDN serves without ad-stitching, so users
    // get an ad-free stream as a side effect of using the raw playback URL.

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
            PlayLink.Kind.CLIP -> emitClipLinks(link.id, link.title, callback)
        }
    }

    private suspend fun emitClipLinks(
        clipUID: String,
        title: String?,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        // Re-fetch the page to get a fresh playback URL — these tokens expire
        // similarly to live HLS auth tokens.
        val html = ChzzkApi.getText(Urls.clip(clipUID))
        val clip = ClipScraper.parse(html) ?: return false
        val streamUrl = clip.playbackUrl ?: return false
        val sourceLabel = "$name (clip)"
        val variants = runCatching {
            M3u8Helper.generateM3u8(
                source = sourceLabel,
                streamUrl = streamUrl,
                referer = Urls.WEB_BASE,
            )
        }.getOrNull()
        if (!variants.isNullOrEmpty()) {
            variants.forEach(callback)
            return true
        }
        callback(
            ExtractorLink(
                source = sourceLabel,
                name = "$sourceLabel · ${title ?: clipUID}",
                url = streamUrl,
                referer = Urls.WEB_BASE,
                quality = 0,
                type = if (streamUrl.contains(".m3u8")) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
            )
        )
        return true
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
        val label = title ?: detail.liveTitle ?: detail.channel.channelName ?: "Chzzk Live"
        var emitted = emitMediaLinks(
            mediaList = playback.media,
            label = label,
            isLive = true,
            callback = callback,
        )
        // Multiview cameras (when enabled by the streamer) appear as additional
        // entries in the player's source menu so users can jump between angles.
        playback.multiview.forEach { mv ->
            val path = mv.path ?: return@forEach
            val sourceLabel = "$name · 멀티뷰" + (mv.name?.let { " ($it)" } ?: "")
            val isM3u8 = path.contains(".m3u8")
            callback(
                ExtractorLink(
                    source = sourceLabel,
                    name = "$sourceLabel · $label",
                    url = path,
                    referer = Urls.WEB_BASE,
                    quality = 0,
                    type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                )
            )
            emitted = true
        }
        return emitted
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
        // For live we offer both standard HLS and low-latency LLHLS as separate
        // entries — users pick from the player menu. For VOD only standard HLS
        // exists in the playback payload, so the loop emits one entry.
        // M3u8Helper.generateM3u8 fetches the master and returns one
        // ExtractorLink per EXT-X-STREAM-INF variant, giving the player a
        // proper quality menu (1080p / 720p / 480p / 360p / 144p).
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
            val sourceLabel = when (media.mediaId.uppercase()) {
                "LLHLS" -> "$name (저지연)"
                "HLS" -> name
                else -> "$name ${media.mediaId}"
            }
            val variants = runCatching {
                M3u8Helper.generateM3u8(
                    source = sourceLabel,
                    streamUrl = media.path,
                    referer = Urls.WEB_BASE,
                )
            }.getOrNull()

            if (!variants.isNullOrEmpty()) {
                variants.forEach(callback)
                emitted += variants.size
            } else {
                // Master expansion failed — emit a single link pointing at the
                // master m3u8 so ExoPlayer can do its own ABR selection.
                callback(
                    ExtractorLink(
                        source = sourceLabel,
                        name = "$sourceLabel · $label",
                        url = media.path,
                        referer = Urls.WEB_BASE,
                        quality = qualityFromTracks(media.encodingTrack),
                        type = ExtractorLinkType.M3U8,
                    )
                )
                emitted++
            }
        }
        return emitted > 0
    }

    // ----------------------------------------------------------------- helpers

    private suspend fun fetchLiveDetail(channelId: String): LiveDetail {
        val raw = ChzzkApi.getText(Endpoints.liveDetail(channelId))
        val res = parseJson<ChzzkResponse<LiveDetail>>(raw)
        ChzzkApi.checkOk(res.code, res.message, "live-detail $channelId")
        return res.content ?: throw ErrorLoadingException("live-detail content 누락")
    }

    private suspend fun fetchVideoDetail(videoNo: Long): VideoDetail {
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
    private suspend fun fetchLiveRecommendations(channelId: String): List<SearchResponse> {
        val raw = ChzzkApi.getText(Endpoints.channelLiveRecommended(channelId))
        val res = parseJson<ChzzkResponse<LiveRecommendedResponse>>(raw)
        if (res.code != 200) return emptyList()
        return res.content?.recommendedContents.orEmpty()
            .flatMap { it.lives }
            .filterNot { it.adult || it.channel.channelId == channelId }
            .distinctBy { it.liveId }
            .take(20)
            .map { it.toSearchResponse() }
    }

    /**
     * Walk the page-based VOD list for a channel and return up to
     * [MAX_CHANNEL_VIDEOS] entries. Stops early when the API reports the last
     * page or when the cap is reached. Each page is 30 items, so the default
     * cap of 120 means at most 4 round-trips for the largest channels.
     */
    private suspend fun fetchAllChannelVideos(channelId: String): List<VideoSummary> {
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
        val watchParty = if (watchPartyNo != null) "👥 " else ""
        val title = "$watchParty$verified${channel.channelName ?: channel.channelId}$viewers"
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

    private fun VideoSummary.toMovieSearchResponse(
        channelName: String?,
        prefix: String = "",
    ): MovieSearchResponse {
        val base = listOfNotNull(channelName, videoTitle).joinToString(" · ").ifBlank { "video #$videoNo" }
        return newMovieSearchResponse(
            name = "$prefix$base",
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
        if (detail.timeMachineActive) {
            appendLine()
            append("⏪ 타임머신: 되감기 가능")
        }
        if (detail.watchPartyNo != null) {
            appendLine()
            append("👥 WatchParty #${detail.watchPartyNo}")
            if (!detail.watchPartyTag.isNullOrBlank()) append(" · ${detail.watchPartyTag}")
        }
    }

    /**
     * Append weekly donation top-N, chat rules, café connection, and shop
     * info to a base plot. All fetches are best-effort — failures are
     * silently swallowed because the plot is metadata enrichment, not
     * core functionality.
     */
    private suspend fun augmentPlotWithCommunity(base: String, channelId: String): String {
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

    private fun formatCurrency(amount: Long): String = when {
        amount >= 10_000 -> "%.1f만원".format(amount / 10_000.0)
        else -> "${amount}원"
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
        private const val SECTION_SCHEDULE = "__SCHEDULE__"

        /** Hard cap on VODs collected per channel page (4 API pages). */
        private const val MAX_CHANNEL_VIDEOS = 120
    }
}
