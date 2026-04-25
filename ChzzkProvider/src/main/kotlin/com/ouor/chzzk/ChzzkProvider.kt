package com.ouor.chzzk

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType

class ChzzkProvider : MainAPI() {
    override var mainUrl = "https://chzzk.naver.com"
    override var name = "Chzzk"
    override val supportedTypes = setOf(TvType.Live, TvType.Movie)

    override var lang = "ko"

    override val hasMainPage = true
    override val hasQuickSearch = true

    override suspend fun search(query: String): List<SearchResponse> {
        return emptyList()
    }
}
