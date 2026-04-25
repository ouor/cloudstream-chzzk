package com.ouor.chzzk

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.ouor.chzzk.api.ChzzkApi
import com.ouor.chzzk.api.Endpoints
import com.ouor.chzzk.auth.ChzzkAuth
import com.ouor.chzzk.loader.PROVIDER_NAME
import com.ouor.chzzk.loader.emitPlayLink
import com.ouor.chzzk.loader.loadChannel
import com.ouor.chzzk.loader.loadClip
import com.ouor.chzzk.loader.loadLive
import com.ouor.chzzk.loader.loadVideo
import com.ouor.chzzk.models.ChzzkResponse
import com.ouor.chzzk.models.HomeMain
import com.ouor.chzzk.models.LiveSummary
import com.ouor.chzzk.models.PageData
import com.ouor.chzzk.models.ProgramScheduleList
import com.ouor.chzzk.models.SearchChannelItem
import com.ouor.chzzk.models.StreamerPartnerList
import com.ouor.chzzk.models.VideoSummary

class ChzzkProvider : MainAPI() {
    override var mainUrl = Urls.WEB_BASE
    override var name = PROVIDER_NAME
    override val supportedTypes = setOf(TvType.Live, TvType.Movie)

    override var lang = "ko"

    override val hasMainPage = true
    override val hasQuickSearch = true

    override val mainPage = mainPageOf(
        SECTION_FOLLOWING to "⭐ 팔로잉 라이브",
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
            SECTION_FOLLOWING -> {
                items = loadFollowings()
                hasNext = false
            }
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
                .forEach { if (seen.add(it.liveId)) add(toSearchResponse(it)) }
            home.slots.asSequence().flatMap { it.slotContents.asSequence() }
                .filterNot { hideAdult && it.adult }
                .forEach { if (seen.add(it.liveId)) add(toSearchResponse(it)) }
        }
    }

    /**
     * Followed channels currently live. Requires NID auth — for anonymous
     * users this section is silently empty (the API returns code != 200,
     * which we swallow rather than surfacing as an error). When the user
     * later logs in via the settings fragment + invalidates cache, the
     * section will populate on the next mainPage refresh.
     */
    private suspend fun loadFollowings(): List<SearchResponse> {
        if (!ChzzkAuth.current().isLoggedIn) return emptyList()
        val raw = runCatching { ChzzkApi.getText(Endpoints.followingsLive(size = 30)) }
            .getOrNull() ?: return emptyList()
        val res = runCatching { parseJson<ChzzkResponse<PageData<LiveSummary>>>(raw) }.getOrNull()
        if (res?.code != 200) return emptyList()
        return res.content?.data.orEmpty().map { toSearchResponse(it) }
    }

    private suspend fun loadSchedule(): List<SearchResponse> {
        val raw = ChzzkApi.getText(Endpoints.programSchedulesComing())
        val res = parseJson<ChzzkResponse<ProgramScheduleList>>(raw)
        ChzzkApi.checkOk(res.code, res.message, "program-schedules")
        return res.content?.programSchedules.orEmpty().map { toSearchResponse(it) }
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
        val items = batch.lives.map { toSearchResponse(it) }
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
        val nextMap = pageData?.pageCursor?.next
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
                                results += toMovieSearchResponse(video, item.channel.channelName)
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
                    if (seenChannels.add(live.channel.channelId)) results += toSearchResponse(live)
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
                        results += toMovieSearchResponse(video, video.channel?.channelName)
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

    // ----------------------------------------------------------- load/loadLinks

    override suspend fun load(url: String): LoadResponse? = when {
        Urls.parseLive(url) != null -> loadLive(url, Urls.parseLive(url)!!)
        Urls.parseVideo(url) != null -> loadVideo(url, Urls.parseVideo(url)!!)
        Urls.parseClip(url) != null -> loadClip(url, Urls.parseClip(url)!!)
        Urls.parseChannel(url) != null -> loadChannel(url, Urls.parseChannel(url)!!)
        else -> null
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val link = PlayLink.decode(data) ?: return false
        return emitPlayLink(link, callback)
    }

    companion object {
        private const val SECTION_FOLLOWING = "__FOLLOWING__"
        private const val SECTION_HOME = "__HOME__"
        private const val SECTION_PARTNERS = "__PARTNERS__"
        private const val SECTION_SCHEDULE = "__SCHEDULE__"
    }
}
