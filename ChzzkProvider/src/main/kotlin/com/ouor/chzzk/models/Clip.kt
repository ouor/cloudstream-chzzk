package com.ouor.chzzk.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClipSummary(
    val clipUID: String,
    val videoId: String? = null,
    val clipTitle: String? = null,
    val ownerChannelId: String? = null,
    val thumbnailImageUrl: String? = null,
    val categoryType: String? = null,
    val clipCategory: String? = null,
    val duration: Long = 0L,
    val adult: Boolean = false,
    val createdDate: String? = null,
    val readCount: Int = 0,
    val blindType: String? = null,
)

/**
 * Response of `GET /service/v1/clips/{clipUID}/detail`. Mirrors the
 * official capture under [fixtures/clip-detail.json]. Notable fields:
 *
 * - [videoId] is the same 36-char hex used by VODs / live streams; clip
 *   playback URLs include this id in their CDN path.
 * - [vodStatus] is typically `ABR_HLS` for playable clips. Anything else
 *   should be treated like a missing/expired VOD.
 * - The response does NOT include the m3u8 URL itself — playback metadata
 *   has to be resolved separately (currently via [ClipScraper]).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ClipDetail(
    val clipUID: String,
    val videoId: String? = null,
    val clipTitle: String? = null,
    val thumbnailImageUrl: String? = null,
    val categoryType: String? = null,
    val clipCategory: String? = null,
    val duration: Long = 0L,
    val adult: Boolean = false,
    val blindType: String? = null,
    val krOnlyViewing: Boolean = false,
    val vodStatus: String? = null,
    val recId: String? = null,
    val createdDate: String? = null,
    val commentActive: Boolean = true,
    val optionalProperty: ClipOptionalProperty? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClipOptionalProperty(
    val commentCount: Int? = null,
    val hasDeletePermission: Boolean = false,
    val privateUserBlock: Boolean = false,
    val penalty: Boolean = false,
    val makerChannel: ChannelInfo? = null,
    val ownerChannel: ChannelInfo? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ClipDetailBulk(
    val metaMap: Map<String, ClipDetail> = emptyMap(),
)
