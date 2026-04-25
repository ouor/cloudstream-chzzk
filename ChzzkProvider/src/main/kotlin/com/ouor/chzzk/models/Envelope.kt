package com.ouor.chzzk.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChzzkResponse<T>(
    val code: Int = 0,
    val message: String? = null,
    val content: T? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PageData<T>(
    val size: Int? = null,
    val page: PageCursor? = null,
    val totalCount: Int? = null,
    val totalPages: Int? = null,
    val data: List<T> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class PageCursor(
    val next: Map<String, Any?>? = null,
    val prev: Map<String, Any?>? = null,
)
