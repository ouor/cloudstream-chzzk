package com.ouor.chzzk.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class CafeConnection(
    val cafe: CafeInfo? = null,
    val menu: CafeMenuInfo? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CafeInfo(
    val id: Long? = null,
    val name: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CafeMenuInfo(
    val id: Long? = null,
    val name: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LogPowerWeekly(
    val updatedDate: String? = null,
    val rankList: List<LogPowerRanker> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LogPowerRanker(
    val ranking: Int = 0,
    val nickName: String? = null,
    val profileImageUrl: String? = null,
    val verifiedMark: Boolean = false,
    val logPower: Long = 0L,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class StreamerShopProducts(
    val products: List<StreamerShopProduct> = emptyList(),
    val totalCount: Int = 0,
    val updatedDate: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class StreamerShopProduct(
    val productId: String? = null,
    val productName: String? = null,
    val price: Long? = null,
    val thumbnailUrl: String? = null,
    val landingUrl: String? = null,
)
