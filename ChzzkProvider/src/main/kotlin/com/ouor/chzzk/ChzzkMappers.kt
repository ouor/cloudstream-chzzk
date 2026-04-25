package com.ouor.chzzk

import com.lagradost.cloudstream3.LiveSearchResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.ouor.chzzk.models.EncodingTrack
import com.ouor.chzzk.models.LiveDetail
import com.ouor.chzzk.models.LiveSummary
import com.ouor.chzzk.models.ProgramSchedule
import com.ouor.chzzk.models.VideoDetail
import com.ouor.chzzk.models.VideoSummary

/**
 * Replace the `{type}` placeholder in Chzzk thumbnail URL templates with a
 * concrete size token (default 720). The API ships the template form
 * (`https://.../live/blah/{type}/...jpg`) and expects clients to substitute.
 */
internal fun fillThumb(template: String, type: String = "720"): String =
    template.replace("{type}", type)

internal fun formatViewers(count: Int): String = when {
    count >= 10000 -> "%.1f만명".format(count / 10000.0)
    count >= 1000 -> "${count / 1000}.${(count % 1000) / 100}천명"
    else -> "${count}명"
}

internal fun formatCurrency(amount: Long): String = when {
    amount >= 10_000 -> "%.1f만원".format(amount / 10_000.0)
    else -> "${amount}원"
}

/** Heuristic: report the master's max height; CloudStream uses Quality ints aligned to vertical resolution. */
internal fun qualityFromTracks(tracks: List<EncodingTrack>): Int =
    tracks.mapNotNull { it.videoHeight }.maxOrNull() ?: 0

internal fun buildTags(category: String?, tags: List<String>?): List<String> {
    val out = mutableListOf<String>()
    if (!category.isNullOrBlank()) out += category
    if (tags != null) out += tags
    return out.distinct()
}

internal fun buildPlot(detail: LiveDetail): String = buildString {
    append(detail.channel.channelName ?: detail.channel.channelId)
    if (detail.liveCategoryValue != null) append(" · ${detail.liveCategoryValue}")
    appendLine()
    append("동시 시청자 ${detail.concurrentUserCount}명")
    if (!detail.openDate.isNullOrBlank()) {
        appendLine()
        append("방송 시작 ${detail.openDate}")
    }
    if (detail.timeMachineActive) {
        appendLine()
        append("⏪ 타임머신: 되감기 가능")
    }
    if (detail.watchPartyNo != null) {
        appendLine()
        append("👥 WatchParty #${detail.watchPartyNo}")
        if (!detail.watchPartyTag.isNullOrBlank()) append(" · ${detail.watchPartyTag}")
    }
}

internal fun buildVideoPlot(detail: VideoDetail): String = buildString {
    if (detail.channel?.channelName != null) appendLine(detail.channel.channelName)
    if (detail.videoCategoryValue != null) appendLine("카테고리: ${detail.videoCategoryValue}")
    if (!detail.publishDate.isNullOrBlank()) appendLine("게시 ${detail.publishDate}")
    append("조회 ${detail.readCount}회")
}

// SearchResponse builders below are MainAPI extensions because newLiveSearchResponse /
// newMovieSearchResponse are themselves MainAPI extensions in the CloudStream stub.
// Call sites are MainAPI subclasses (ChzzkProvider) or other MainAPI extensions, so
// the implicit receiver flows through naturally.

internal fun MainAPI.toSearchResponse(summary: LiveSummary): LiveSearchResponse {
    val verified = if (summary.channel.verifiedMark) "✓ " else ""
    val viewers = if (summary.concurrentUserCount > 0) " · ${formatViewers(summary.concurrentUserCount)}" else ""
    val watchParty = if (summary.watchPartyNo != null) "👥 " else ""
    val title = "$watchParty$verified${summary.channel.channelName ?: summary.channel.channelId}$viewers"
    return newLiveSearchResponse(
        name = if (summary.liveTitle.isNullOrBlank()) title else "$title · ${summary.liveTitle}",
        url = Urls.live(summary.channel.channelId),
        type = TvType.Live,
    ) {
        posterUrl = summary.liveImageUrl?.let { fillThumb(it) }
            ?: summary.defaultThumbnailImageUrl
            ?: summary.channel.channelImageUrl
    }
}

internal fun MainAPI.toMovieSearchResponse(
    summary: VideoSummary,
    channelName: String?,
    prefix: String = "",
): MovieSearchResponse {
    val base = listOfNotNull(channelName, summary.videoTitle).joinToString(" · ")
        .ifBlank { "video #${summary.videoNo}" }
    return newMovieSearchResponse(
        name = "$prefix$base",
        url = Urls.video(summary.videoNo),
        type = TvType.Movie,
    ) {
        posterUrl = summary.thumbnailImageUrl
    }
}

internal fun MainAPI.toSearchResponse(schedule: ProgramSchedule): LiveSearchResponse {
    val ch = schedule.channel
    val title = listOfNotNull(schedule.scheduleDate, schedule.scheduleTitle).joinToString(" · ")
    val name = listOfNotNull(ch?.channelName, title).joinToString(" — ")
        .ifBlank { schedule.scheduleTitle ?: "방송 예정" }
    val targetUrl = if (ch != null) Urls.channel(ch.channelId) else Urls.WEB_BASE
    return newLiveSearchResponse(
        name = name,
        url = targetUrl,
        type = TvType.Live,
    ) {
        posterUrl = ch?.channelImageUrl
    }
}
