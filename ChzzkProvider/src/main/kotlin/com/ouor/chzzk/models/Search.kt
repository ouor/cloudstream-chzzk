package com.ouor.chzzk.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchChannelItem(
    val channel: ChannelInfo,
    val content: SearchChannelContent? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SearchChannelContent(
    val live: LiveDetail? = null,
    val videos: List<VideoSummary> = emptyList(),
)
