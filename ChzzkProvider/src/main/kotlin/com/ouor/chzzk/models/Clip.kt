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
 *   has to be resolved via the api-videohub.naver.com /shortformhub
 *   /feeds/v9/card endpoint (see [ShortformCardEnvelope]).
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

/**
 * Response of `GET https://api-videohub.naver.com/shortformhub/feeds/v9/card`.
 * The play-info content lives in `card.content.vod.playback` as a DASH-shaped
 * tree where field names use the MPD vocabulary (Period, AdaptationSet,
 * Representation, BaseURL). Jackson cannot use field names starting with `@`
 * in Kotlin source, so MPD attribute fields are rebound via @JsonProperty.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ShortformCardEnvelope(
    val card: ShortformCard? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShortformCard(
    val content: ShortformCardContent? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShortformCardContent(
    val mediaId: String? = null,
    val title: String? = null,
    val description: String? = null,
    val mediaType: String? = null,
    val publishedAt: String? = null,
    val vod: ShortformVod? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShortformVod(
    val playable: Boolean = false,
    val count: Long? = null,
    val playback: ShortformPlayback? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShortformPlayback(
    @com.fasterxml.jackson.annotation.JsonProperty("\$version") val version: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("MPD") val mpd: List<ShortformMpd> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShortformMpd(
    @com.fasterxml.jackson.annotation.JsonProperty("Period") val period: List<ShortformPeriod> = emptyList(),
    @com.fasterxml.jackson.annotation.JsonProperty("@mediaPresentationDuration") val mediaPresentationDuration: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShortformPeriod(
    @com.fasterxml.jackson.annotation.JsonProperty("AdaptationSet") val adaptationSet: List<ShortformAdaptationSet> = emptyList(),
    @com.fasterxml.jackson.annotation.JsonProperty("@duration") val duration: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShortformAdaptationSet(
    @com.fasterxml.jackson.annotation.JsonProperty("Representation") val representation: List<ShortformRepresentation> = emptyList(),
    @com.fasterxml.jackson.annotation.JsonProperty("@mimeType") val mimeType: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ShortformRepresentation(
    @com.fasterxml.jackson.annotation.JsonProperty("BaseURL") val baseUrl: List<String> = emptyList(),
    @com.fasterxml.jackson.annotation.JsonProperty("@id") val id: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("@bandwidth") val bandwidth: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("@width") val width: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("@height") val height: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("@codecs") val codecs: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("@frameRate") val frameRate: String? = null,
    @com.fasterxml.jackson.annotation.JsonProperty("@mimeType") val mimeType: String? = null,
)
