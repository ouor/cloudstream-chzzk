package com.ouor.chzzk.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChzzkResponse<T>(
    val code: Int = 0,
    val message: String? = null,
    val content: T? = null,
)

/**
 * Generic paginated envelope. Chzzk's `page` field is polymorphic in the
 * wild — for cursor-based endpoints (category lives, search) it is an
 * object `{next, prev}`, for page-based endpoints (channel videos,
 * channel/{id}/donations/missions) it is a flat integer (the current page
 * index). We therefore declare it as `Any?` and expose [pageCursor] /
 * [pageIndex] helpers so callers can pick the shape they want without
 * fighting Jackson over union types.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class PageData<T>(
    val size: Int? = null,
    val page: Any? = null,
    val totalCount: Int? = null,
    val totalPages: Int? = null,
    val data: List<T> = emptyList(),
) {
    /** Cursor view of [page] when the endpoint returns `{next, prev}`. */
    @Suppress("UNCHECKED_CAST")
    val pageCursor: PageCursor?
        get() {
            val map = page as? Map<String, Any?> ?: return null
            return PageCursor(
                next = map["next"] as? Map<String, Any?>,
                prev = map["prev"] as? Map<String, Any?>,
            )
        }

    /** Index view of [page] when the endpoint returns a bare integer. */
    val pageIndex: Int?
        get() = (page as? Number)?.toInt()
}

data class PageCursor(
    val next: Map<String, Any?>? = null,
    val prev: Map<String, Any?>? = null,
)
