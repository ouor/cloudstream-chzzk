package com.ouor.chzzk

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.ouor.chzzk.api.ChzzkApi
import com.ouor.chzzk.api.Endpoints
import com.ouor.chzzk.models.ChzzkResponse
import com.ouor.chzzk.models.HomeMain
import com.ouor.chzzk.models.LiveSummary
import com.ouor.chzzk.models.PageData
import com.ouor.chzzk.models.SearchChannelItem
import com.ouor.chzzk.models.StreamerPartnerList
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

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items: List<SearchResponse> = when (val key = request.data) {
            SECTION_HOME -> loadHome()
            SECTION_PARTNERS -> loadPartners()
            else -> loadCategory(key, page)
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
            home.topLives.forEach {
                if (seen.add(it.liveId)) add(it.toSearchResponse())
            }
            home.slots.flatMap { it.slotContents }.forEach {
                if (seen.add(it.liveId)) add(it.toSearchResponse())
            }
        }
    }

    private suspend fun loadPartners(): List<SearchResponse> {
        val raw = ChzzkApi.get(Endpoints.streamerPartners()).text
        val res = parseJson<ChzzkResponse<StreamerPartnerList>>(raw)
        ChzzkApi.checkOk(res.code, res.message, "streamer partners")
        return res.content?.streamerPartners.orEmpty()
            .filter { it.openLive }
            .map { partner ->
                newLiveSearchResponse(
                    name = partner.channelName ?: partner.originalNickname ?: partner.channelId,
                    url = Urls.live(partner.channelId),
                    type = TvType.Live,
                ) {
                    posterUrl = partner.channelImageUrl
                }
            }
    }

    private suspend fun loadCategory(key: String, page: Int): List<SearchResponse> {
        val parts = key.split("/", limit = 2)
        if (parts.size != 2) return emptyList()
        val (type, id) = parts
        val url = Endpoints.categoryLives(type, id, size = 30)
        val raw = ChzzkApi.get(url).text
        val res = parseJson<ChzzkResponse<PageData<LiveSummary>>>(raw)
        ChzzkApi.checkOk(res.code, res.message, "category lives $key")
        return res.content?.data.orEmpty().map { it.toSearchResponse() }
    }

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
                if (seenChannels.add(live.channel.channelId)) {
                    results += live.toSearchResponse()
                }
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

    private fun LiveSummary.toSearchResponse(): LiveSearchResponse =
        newLiveSearchResponse(
            name = "${channel.channelName ?: channel.channelId} · ${liveTitle ?: ""}".trim(),
            url = Urls.live(channel.channelId),
            type = TvType.Live,
        ) {
            posterUrl = liveImageUrl?.let { fillThumb(it) } ?: defaultThumbnailImageUrl ?: channel.channelImageUrl
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

    companion object {
        private const val SECTION_HOME = "__HOME__"
        private const val SECTION_PARTNERS = "__PARTNERS__"
    }
}
