package com.ouor.chzzk.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class CategorySummary(
    val categoryType: String,
    val categoryId: String,
    val categoryValue: String? = null,
    val posterImageUrl: String? = null,
    val openLiveCount: Int = 0,
    val concurrentUserCount: Int = 0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProgramScheduleList(
    val programSchedules: List<ProgramSchedule> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ProgramSchedule(
    val seq: Long? = null,
    val type: String? = null,
    val scheduleDate: String? = null,
    val scheduleTitle: String? = null,
    val pcNoticeUrl: String? = null,
    val channel: ChannelInfo? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class BannerList(
    val banners: List<Banner> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Banner(
    val position: String? = null,
    val bannerNo: Int? = null,
    val ad: Boolean = false,
    val imageUrl: String? = null,
    val lightThemeImageUrl: String? = null,
    val landingUrl: String? = null,
    val title: String? = null,
    val subCopy: String? = null,
    val scheduledDate: String? = null,
)
