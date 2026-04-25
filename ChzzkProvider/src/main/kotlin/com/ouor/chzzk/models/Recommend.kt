package com.ouor.chzzk.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class LiveRecommendedResponse(
    val recommendedContents: List<RecommendedContent> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RecommendedContent(
    val type: String? = null,
    val title: String? = null,
    val lives: List<LiveSummary> = emptyList(),
)
