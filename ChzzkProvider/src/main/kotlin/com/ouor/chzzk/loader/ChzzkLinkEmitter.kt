package com.ouor.chzzk.loader

import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.ouor.chzzk.PlayLink
import com.ouor.chzzk.Urls
import com.ouor.chzzk.api.ChzzkApi
import com.ouor.chzzk.api.Endpoints
import com.ouor.chzzk.api.fetchLiveDetail
import com.ouor.chzzk.api.fetchVideoDetail
import com.ouor.chzzk.auth.ChzzkAuth
import com.ouor.chzzk.models.ChzzkResponse
import com.ouor.chzzk.models.ClipDetail
import com.ouor.chzzk.models.LivePlayback
import com.ouor.chzzk.models.PlaybackMedia
import com.ouor.chzzk.models.RmcnmvPlayInfo
import com.ouor.chzzk.models.ShortformCardEnvelope
import com.ouor.chzzk.qualityFromTracks
import com.ouor.chzzk.settings.ChzzkSettings

/**
 * Provider-facing label used in the player source menu. Kept as a separate
 * constant so [ChzzkLinkEmitter] does not need a back-reference to the
 * `ChzzkProvider.name` property.
 */
internal const val PROVIDER_NAME = "Chzzk"

// -------------------------------------------------------------- loadLinks
//
// Chzzk's `playerAdFlag` (preRoll/midRoll) governs ad insertion in the
// official web player. We deliberately ignore it here — we pull the HLS
// master directly, which the CDN serves without ad-stitching, so users
// get an ad-free stream as a side effect of using the raw playback URL.

internal suspend fun emitPlayLink(
    link: PlayLink,
    callback: (ExtractorLink) -> Unit,
): Boolean = when (link.kind) {
    PlayLink.Kind.LIVE -> emitLiveLinks(link.id, link.title, callback)
    PlayLink.Kind.VOD -> emitVodLinks(link.id.toLong(), link.title, callback)
    PlayLink.Kind.CLIP -> emitClipLinks(link.id, link.title, link.extra, callback)
}

private suspend fun emitClipLinks(
    clipUID: String,
    title: String?,
    videoId: String?,
    callback: (ExtractorLink) -> Unit,
): Boolean {
    // Resolve videoId: usually carried via PlayLink.extra (set by loadClip),
    // but fall back to a fresh clipDetail fetch if dataUrl came from an
    // older cached entry that pre-dates the extra field.
    val resolvedVideoId = videoId ?: run {
        val raw = ChzzkApi.getText(Endpoints.clipDetail(clipUID))
        parseJson<ChzzkResponse<ClipDetail>>(raw).content?.videoId
    } ?: throw ErrorLoadingException("클립 videoId 조회 실패 ($clipUID).")

    return emitFromVideohub(
        playInfoUrl = Endpoints.clipPlayInfo(
            videoId = resolvedVideoId, clipUID = clipUID, recId = null,
        ),
        label = title ?: clipUID,
        sourceLabel = "$PROVIDER_NAME (clip)",
        errorTag = "clip $clipUID",
        callback = callback,
    )
}

private suspend fun emitLiveLinks(
    channelId: String,
    title: String?,
    callback: (ExtractorLink) -> Unit,
): Boolean {
    val detail = fetchLiveDetail(channelId)
    val playbackJson = detail.livePlaybackJson
        ?: throw ErrorLoadingException("livePlaybackJson 누락")
    val playback = parseJson<LivePlayback>(playbackJson)
    val label = title ?: detail.liveTitle ?: detail.channel.channelName ?: "Chzzk Live"
    var emitted = emitMediaLinks(
        mediaList = playback.media,
        label = label,
        callback = callback,
    )
    // Multiview cameras (when enabled by the streamer) appear as additional
    // entries in the player's source menu so users can jump between angles.
    playback.multiview.forEach { mv ->
        val path = mv.path ?: return@forEach
        val sourceLabel = "$PROVIDER_NAME · 멀티뷰" + (mv.name?.let { " ($it)" } ?: "")
        val isM3u8 = path.contains(".m3u8")
        callback(
            ExtractorLink(
                source = sourceLabel,
                name = "$sourceLabel · $label",
                url = path,
                referer = Urls.WEB_BASE,
                quality = 0,
                type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
            )
        )
        emitted = true
    }
    return emitted
}

private suspend fun emitVodLinks(
    videoNo: Long,
    title: String?,
    callback: (ExtractorLink) -> Unit,
): Boolean {
    val detail = fetchVideoDetail(videoNo)
    // Same playability guards as ChzzkLoader.loadVideo. The search→VOD path
    // hits those guards in load(); the channel→episode path bypasses load()
    // entirely (PlayLink decode goes straight to emit), so without these
    // guards an EXPIRED/PROCESSING/blinded VOD silently returned false here
    // and surfaced the generic "링크를 찾을 수 없음" toast.
    val playableStatuses = setOf("NONE", "ABR_HLS")
    if (detail.vodStatus != null && detail.vodStatus !in playableStatuses) {
        throw ErrorLoadingException("이 다시보기는 더 이상 시청할 수 없습니다 (${detail.vodStatus}).")
    }
    if (detail.adult && !ChzzkAuth.current().isLoggedIn) {
        throw ErrorLoadingException("성인 영상입니다. 로그인 후 성인 인증된 계정으로 이용해주세요.")
    }
    if (detail.blindType != null) {
        throw ErrorLoadingException("이 영상은 시청이 제한되었습니다 (${detail.blindType}).")
    }
    val label = title ?: detail.videoTitle ?: "video #$videoNo"

    // Path 1: live-rewind VODs ship `liveRewindPlaybackJson` inline (same
    // shape as livePlaybackJson). Use it when present.
    val playbackJson = detail.liveRewindPlaybackJson
    if (!playbackJson.isNullOrBlank()) {
        val playback = parseJson<LivePlayback>(playbackJson)
        return emitMediaLinks(
            mediaList = playback.media,
            label = label,
            callback = callback,
        )
    }

    // Path 2: ABR_HLS VODs do NOT embed liveRewindPlaybackJson. The
    // official web player calls Naver's RMC video player API (apis.naver.com
    // /rmcnmv/...) using the `inKey` token from videoDetail. The
    // /shortformhub endpoint that clips use returns errorCode=GET_FAILED
    // for these — verified against video #12893353 on 2026-04-26.
    val videoId = detail.videoId
        ?: throw ErrorLoadingException(
            "재생 URL을 찾지 못했습니다 (videoId 누락, vodStatus=${detail.vodStatus})."
        )
    val inKey = detail.inKey
        ?: throw ErrorLoadingException(
            "재생 URL을 찾지 못했습니다 (inKey 누락, vodStatus=${detail.vodStatus})."
        )
    return emitFromRmcnmv(
        videoId = videoId,
        inKey = inKey,
        label = label,
        errorTag = "video #$videoNo",
        callback = callback,
    )
}

/**
 * Hits Naver's RMC video player play-info API and emits ExtractorLinks
 * for every quality available. Strategy:
 *   1. Emit each progressive MP4 from `videos.list[]` — these are
 *      single-quality, single-file streams with the auth token already
 *      embedded in the URL. Most reliable for downstream players.
 *   2. Then emit the HLS master from `streams[type=HLS]` with the
 *      `_lsu_sa_` query token appended, so users who prefer ABR can
 *      pick the master playlist from the source menu.
 */
private suspend fun emitFromRmcnmv(
    videoId: String,
    inKey: String,
    label: String,
    errorTag: String,
    callback: (ExtractorLink) -> Unit,
): Boolean {
    val raw = ChzzkApi.getText(Endpoints.rmcnmvVodPlay(videoId = videoId, inKey = inKey))
    val info = parseJson<RmcnmvPlayInfo>(raw)
    var emitted = 0

    // Sort MP4 entries by descending height so the largest quality lands
    // at the top of the player source menu.
    val mp4Entries = info.videos?.list.orEmpty()
        .filter { !it.source.isNullOrBlank() }
        .sortedByDescending { it.encodingOption?.height ?: 0 }
    for (entry in mp4Entries) {
        val height = entry.encodingOption?.height ?: 0
        val qualityName = entry.encodingOption?.name ?: entry.encodingOption?.id ?: "MP4"
        callback(
            ExtractorLink(
                source = PROVIDER_NAME,
                name = "$PROVIDER_NAME · $label · $qualityName",
                url = entry.source!!,
                referer = Urls.WEB_BASE,
                quality = height,
                type = ExtractorLinkType.VIDEO,
            )
        )
        emitted++
    }

    // HLS master playlist for ABR-capable players. The `keys` list carries
    // a single `{type: "param", name: "_lsu_sa_", value: ...}` entry that
    // must be appended to every HLS URL.
    val hls = info.streams.firstOrNull { it.type.equals("HLS", ignoreCase = true) }
    val hlsSource = hls?.source
    if (hls != null && !hlsSource.isNullOrBlank()) {
        val authQuery = hls.keys
            .filter { it.type == "param" && !it.name.isNullOrBlank() && !it.value.isNullOrBlank() }
            .joinToString("&") { "${it.name}=${it.value}" }
        val finalUrl = if (authQuery.isBlank()) hlsSource
        else if (hlsSource.contains('?')) "$hlsSource&$authQuery"
        else "$hlsSource?$authQuery"
        callback(
            ExtractorLink(
                source = PROVIDER_NAME,
                name = "$PROVIDER_NAME · $label · HLS (ABR)",
                url = finalUrl,
                referer = Urls.WEB_BASE,
                quality = 0,
                type = ExtractorLinkType.M3U8,
            )
        )
        emitted++
    }

    if (emitted == 0) {
        throw ErrorLoadingException("재생 정보를 찾지 못했습니다 ($errorTag).")
    }
    return true
}

/**
 * Hits api-videohub.naver.com /shortformhub/feeds/v9/card and emits one
 * ExtractorLink per AdaptationSet Representation. MP4 variants are
 * sorted ahead of HLS so the player auto-picks the simpler stream.
 * Shared by clip and ABR_HLS VOD playback paths.
 */
private suspend fun emitFromVideohub(
    playInfoUrl: String,
    label: String,
    sourceLabel: String,
    errorTag: String,
    callback: (ExtractorLink) -> Unit,
): Boolean {
    val raw = ChzzkApi.getText(playInfoUrl)
    val envelope = parseJson<ShortformCardEnvelope>(raw)
    val vod = envelope.card?.content?.vod
    if (vod?.playable != true) {
        throw ErrorLoadingException("재생할 수 없습니다 ($errorTag).")
    }
    val adaptationSets = vod.playback?.mpd?.firstOrNull()
        ?.period?.firstOrNull()
        ?.adaptationSet
        .orEmpty()
    if (adaptationSets.isEmpty()) {
        throw ErrorLoadingException("재생 정보를 찾지 못했습니다 ($errorTag).")
    }
    val ordered = adaptationSets.sortedBy { aset ->
        when (aset.mimeType) {
            "video/mp4" -> 0
            "video/mp2t", "application/vnd.apple.mpegurl" -> 1
            else -> 2
        }
    }
    var emitted = 0
    for (aset in ordered) {
        val rep = aset.representation.firstOrNull() ?: continue
        val url = rep.baseUrl.firstOrNull()?.takeIf { it.isNotBlank() } ?: continue
        val isM3u8 = aset.mimeType?.contains("mpegurl", ignoreCase = true) == true ||
                aset.mimeType == "video/mp2t" ||
                url.contains(".m3u8")
        val height = rep.height?.toIntOrNull() ?: 0
        callback(
            ExtractorLink(
                source = sourceLabel,
                name = "$sourceLabel · $label (${aset.mimeType ?: "?"})",
                url = url,
                referer = Urls.WEB_BASE,
                quality = height,
                type = if (isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
            )
        )
        emitted++
    }
    return emitted > 0
}

private suspend fun emitMediaLinks(
    mediaList: List<PlaybackMedia>,
    label: String,
    callback: (ExtractorLink) -> Unit,
): Boolean {
    // For live we offer both standard HLS and low-latency LLHLS as separate
    // entries — users pick from the player menu. For VOD only standard HLS
    // exists in the playback payload, so the loop emits one entry.
    // M3u8Helper.generateM3u8 fetches the master and returns one
    // ExtractorLink per EXT-X-STREAM-INF variant, giving the player a
    // proper quality menu (1080p / 720p / 480p / 360p / 144p).
    //
    // Order is normally HLS-first, but the user can flip the preference
    // via the settings fragment (ChzzkSettings.preferLowLatency). When
    // flipped, LLHLS is sorted first so the player auto-selects the
    // low-latency variant.
    val preferLlhls = ChzzkSettings.current().preferLowLatency
    val ordered = mediaList.sortedBy {
        when (it.mediaId) {
            "HLS" -> if (preferLlhls) 1 else 0
            "LLHLS" -> if (preferLlhls) 0 else 1
            else -> 2
        }
    }
    var emitted = 0
    for (media in ordered) {
        if (media.protocol?.uppercase() != "HLS") continue
        val sourceLabel = when (media.mediaId.uppercase()) {
            "LLHLS" -> "$PROVIDER_NAME (저지연)"
            "HLS" -> PROVIDER_NAME
            else -> "$PROVIDER_NAME ${media.mediaId}"
        }
        val variants = runCatching {
            M3u8Helper.generateM3u8(
                source = sourceLabel,
                streamUrl = media.path,
                referer = Urls.WEB_BASE,
            )
        }.getOrNull()

        if (!variants.isNullOrEmpty()) {
            variants.forEach(callback)
            emitted += variants.size
        } else {
            // Master expansion failed — emit a single link pointing at the
            // master m3u8 so ExoPlayer can do its own ABR selection.
            callback(
                ExtractorLink(
                    source = sourceLabel,
                    name = "$sourceLabel · $label",
                    url = media.path,
                    referer = Urls.WEB_BASE,
                    quality = qualityFromTracks(media.encodingTrack),
                    type = ExtractorLinkType.M3U8,
                )
            )
            emitted++
        }
    }
    return emitted > 0
}
