package com.ouor.chzzk.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class LiveSummary(
    val liveId: Long,
    val liveTitle: String? = null,
    val liveImageUrl: String? = null,
    val defaultThumbnailImageUrl: String? = null,
    val concurrentUserCount: Int = 0,
    val accumulateCount: Int? = null,
    val openDate: String? = null,
    val adult: Boolean = false,
    val tags: List<String>? = null,
    val categoryType: String? = null,
    val liveCategory: String? = null,
    val liveCategoryValue: String? = null,
    val channel: ChannelInfo,
    val blindType: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LiveDetail(
    val liveId: Long,
    val liveTitle: String? = null,
    val status: String? = null,
    val liveImageUrl: String? = null,
    val defaultThumbnailImageUrl: String? = null,
    val concurrentUserCount: Int = 0,
    val accumulateCount: Int? = null,
    val openDate: String? = null,
    val closeDate: String? = null,
    val adult: Boolean = false,
    val krOnlyViewing: Boolean = false,
    val clipActive: Boolean = false,
    val tags: List<String>? = null,
    val chatChannelId: String? = null,
    val categoryType: String? = null,
    val liveCategory: String? = null,
    val liveCategoryValue: String? = null,
    val livePlaybackJson: String? = null,
    val livePollingStatusJson: String? = null,
    val channel: ChannelInfo,
    val blindType: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AutoPlayInfo(
    val livePlaybackJson: String? = null,
    val livePollingStatusJson: String? = null,
    val openDate: String? = null,
    val liveRewindPlaybackJson: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LivePlayback(
    val meta: PlaybackMeta? = null,
    val media: List<PlaybackMedia> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PlaybackMeta(
    val videoId: String? = null,
    val streamSeq: Long? = null,
    val liveId: String? = null,
    val paidLive: Boolean = false,
    val playbackAuthType: String? = null,
    val liveRewind: Boolean = false,
    val duration: Double? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PlaybackMedia(
    val mediaId: String,
    val protocol: String? = null,
    val path: String,
    val encodingTrack: List<EncodingTrack> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EncodingTrack(
    val encodingTrackId: String,
    val videoCodec: String? = null,
    val videoBitRate: Long? = null,
    val videoFrameRate: String? = null,
    val videoWidth: Int? = null,
    val videoHeight: Int? = null,
    val audioBitRate: Long? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class HomeMain(
    val topLives: List<LiveSummary> = emptyList(),
    val slots: List<HomeSlot> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class HomeSlot(
    val slotNo: Int? = null,
    val slotTitle: String? = null,
    val slotContentType: String? = null,
    val slotContents: List<LiveSummary> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class StreamerPartnerList(
    val streamerPartners: List<StreamerPartner> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class StreamerPartner(
    val channelId: String,
    val channelImageUrl: String? = null,
    val originalNickname: String? = null,
    val channelName: String? = null,
    val verifiedMark: Boolean = false,
    val openLive: Boolean = false,
    val newStreamer: Boolean = false,
    val liveTitle: String? = null,
    val concurrentUserCount: Int = 0,
    val liveCategoryValue: String? = null,
)
