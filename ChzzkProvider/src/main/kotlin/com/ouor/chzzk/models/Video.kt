package com.ouor.chzzk.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class VideoSummary(
    val videoNo: Long,
    val videoId: String? = null,
    val videoTitle: String? = null,
    val videoType: String? = null,
    val publishDate: String? = null,
    val publishDateAt: Long? = null,
    val thumbnailImageUrl: String? = null,
    val trailerUrl: String? = null,
    val duration: Long = 0L,
    val readCount: Int = 0,
    val livePv: Int? = null,
    val categoryType: String? = null,
    val videoCategory: String? = null,
    val videoCategoryValue: String? = null,
    val adult: Boolean = false,
    val clipActive: Boolean = false,
    val exposure: Boolean = true,
    val tags: List<String>? = null,
    val channel: ChannelInfo? = null,
    val blindType: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VideoDetail(
    val videoNo: Long,
    val videoId: String? = null,
    val videoTitle: String? = null,
    val videoType: String? = null,
    val publishDate: String? = null,
    val publishDateAt: Long? = null,
    val thumbnailImageUrl: String? = null,
    val trailerUrl: String? = null,
    val duration: Long = 0L,
    val readCount: Int = 0,
    val livePv: Int? = null,
    val categoryType: String? = null,
    val videoCategory: String? = null,
    val videoCategoryValue: String? = null,
    val adult: Boolean = false,
    val clipActive: Boolean = false,
    val exposure: Boolean = true,
    val tags: List<String>? = null,
    val channel: ChannelInfo? = null,
    val blindType: String? = null,
    val liveOpenDate: String? = null,
    val vodStatus: String? = null,
    val liveRewindPlaybackJson: String? = null,
    val prevVideo: VideoSummary? = null,
    val nextVideo: VideoSummary? = null,
)
