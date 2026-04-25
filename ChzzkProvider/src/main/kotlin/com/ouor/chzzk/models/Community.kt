package com.ouor.chzzk.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class ChatRules(
    val agree: Boolean = false,
    val channelId: String? = null,
    val rule: String? = null,
    val updatedDate: String? = null,
    val serviceAgree: Boolean = false,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DonationRankResponse(
    val rankList: List<DonationRanker> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class DonationRanker(
    val userIdHash: String? = null,
    val nickName: String? = null,
    val profileImageUrl: String? = null,
    val verifiedMark: Boolean = false,
    val donationAmount: Long = 0L,
    val ranking: Int = 0,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VideoChatList(
    val nextPlayerMessageTime: Long? = null,
    val previousVideoChats: List<VideoChat> = emptyList(),
    val videoChats: List<VideoChat> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VideoChat(
    val chatChannelId: String? = null,
    val messageTime: Long = 0L,
    val playerMessageTime: Long = 0L,
    val userIdHash: String? = null,
    val content: String? = null,
    val extras: String? = null,
    val messageTypeCode: Int = 0,
    val messageStatusType: String? = null,
    val profile: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NicknameColorCodes(
    val codeList: List<NicknameColor> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class NicknameColor(
    val code: String? = null,
    val darkRgbValue: String? = null,
    val lightRgbValue: String? = null,
    val availableScope: String? = null,
)
