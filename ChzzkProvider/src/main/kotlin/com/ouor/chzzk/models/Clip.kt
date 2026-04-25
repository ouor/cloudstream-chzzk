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
