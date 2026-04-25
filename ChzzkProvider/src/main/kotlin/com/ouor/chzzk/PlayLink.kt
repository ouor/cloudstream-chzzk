package com.ouor.chzzk

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Serialized payload passed from [ChzzkProvider.load] to [ChzzkProvider.loadLinks]
 * via [LoadResponse.dataUrl]. Carries either a live `channelId` or a VOD `videoNo`
 * and the title used to label extractor entries.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PlayLink(
    val kind: Kind,
    val id: String,
    val title: String? = null,
    /**
     * Extra payload encoded after the title. Currently only used by
     * [Kind.CLIP] to carry the 36-char hex `videoId` so [ChzzkProvider.emitClipLinks]
     * can build the api-videohub play-info URL without re-fetching clipDetail.
     */
    val extra: String? = null,
) {
    enum class Kind { LIVE, VOD, CLIP }
}
