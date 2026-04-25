package com.ouor.chzzk

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Serialized payload passed from [com.ouor.chzzk.loader.ChzzkLoader] to
 * [ChzzkProvider.loadLinks] via `LoadResponse.dataUrl` (and per-Episode `data`
 * for channel TvSeries). Carries either a live `channelId`, a VOD `videoNo`,
 * or a clip UID, along with the title used to label extractor entries.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PlayLink(
    val kind: Kind,
    val id: String,
    val title: String? = null,
    /**
     * Extra payload encoded after the title. Currently only used by
     * [Kind.CLIP] to carry the 36-char hex `videoId` so the clip emitter
     * can build the api-videohub play-info URL without re-fetching clipDetail.
     */
    val extra: String? = null,
) {
    enum class Kind { LIVE, VOD, CLIP }

    /** Pipe-delimited wire format: `KIND|id|title|extra`. */
    fun encode(): String = listOf(
        kind.name,
        id,
        title?.replace("|", "／").orEmpty(),
        extra?.replace("|", "／").orEmpty(),
    ).joinToString("|")

    companion object {
        fun decode(data: String): PlayLink? {
            val parts = data.split("|", limit = 4)
            if (parts.size < 2) return null
            val kind = runCatching { Kind.valueOf(parts[0]) }.getOrNull() ?: return null
            return PlayLink(
                kind = kind,
                id = parts[1],
                title = parts.getOrNull(2)?.takeIf { it.isNotBlank() },
                extra = parts.getOrNull(3)?.takeIf { it.isNotBlank() },
            )
        }
    }
}
