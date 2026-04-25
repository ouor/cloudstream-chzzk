package com.ouor.chzzk.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Response of `GET https://apis.naver.com/rmcnmv/rmcnmv/vod/play/v2.0/{videoId}?key={inKey}`,
 * Naver's standard RMC video player play-info API. ABR_HLS VODs (the long-form
 * uploaded VODs at chzzk.naver.com/video/{n}) resolve their playback URL
 * here, while clips use the separate /shortformhub endpoint.
 *
 * Verified against video #12893353 on 2026-04-26.
 *
 * Two playback modes are surfaced in the response:
 *   - [RmcnmvVideos.list] — progressive MP4 per encoding option. The `source`
 *     URL is signed (`_lsu_sa_=...` query param embedded), ready to GET.
 *   - [streams] — DASH/HLS streams. For HLS, [RmcnmvStream.source] is the
 *     master m3u8 and the auth token must be appended via the [RmcnmvStream.keys]
 *     entries (one entry of `{type:"param", name:"_lsu_sa_", value:"..."}`).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class RmcnmvPlayInfo(
    val meta: RmcnmvMeta? = null,
    val videos: RmcnmvVideos? = null,
    val streams: List<RmcnmvStream> = emptyList(),
    val expireTime: String? = null,
    val serverTime: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RmcnmvMeta(
    val masterVideoId: String? = null,
    val contentId: String? = null,
    val subject: String? = null,
    val count: Long? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RmcnmvVideos(
    val list: List<RmcnmvVideoEntry> = emptyList(),
    val type: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RmcnmvVideoEntry(
    val id: String? = null,
    val source: String? = null,
    val type: String? = null,
    val encodingOption: RmcnmvEncodingOption? = null,
    val bitrate: RmcnmvBitrate? = null,
    val size: Long? = null,
    val duration: Double? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RmcnmvEncodingOption(
    val id: String? = null,
    val name: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val bitrate: RmcnmvBitrate? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RmcnmvBitrate(
    val video: Double? = null,
    val audio: Double? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RmcnmvStream(
    val type: String? = null,
    val enableABR: String? = null,
    val source: String? = null,
    val keys: List<RmcnmvKey> = emptyList(),
    val videos: List<RmcnmvVideoEntry> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class RmcnmvKey(
    val type: String? = null,
    val name: String? = null,
    val value: String? = null,
)
