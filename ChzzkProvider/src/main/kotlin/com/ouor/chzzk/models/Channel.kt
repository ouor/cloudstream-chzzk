package com.ouor.chzzk.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChannelInfo(
    val channelId: String,
    val channelName: String? = null,
    val channelImageUrl: String? = null,
    val verifiedMark: Boolean = false,
    val channelType: String? = null,
    val channelDescription: String? = null,
    val followerCount: Int? = null,
    val openLive: Boolean = false,
    val activatedChannelBadgeIds: List<String> = emptyList(),
)
