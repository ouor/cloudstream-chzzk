# Chzzk API 명세

NAVER 치지직(`api.chzzk.naver.com`) HTTP API 명세서. CloudStream provider 구현을 위한 캡처 기반 리버스 엔지니어링 결과 (2026-04-26 캡처, 119 exchange / 58 unique endpoint).

> 본 문서는 비공식 명세이며, 캡처된 트래픽으로부터 추론된 스키마를 정리한 것입니다. 응답 필드는 시점에 따라 추가/제거될 수 있으므로 파싱 시 `*OrNull` 처리를 권장합니다.

## 목차
1. [공통 사항](#공통-사항)
2. [홈/탐색 (Home & Discovery)](#홈탐색-home--discovery)
3. [검색 (Search)](#검색-search)
4. [채널 (Channel)](#채널-channel)
5. [라이브 (Live)](#라이브-live)
6. [VOD/다시보기 (Video)](#vod다시보기-video)
7. [클립 (Clip)](#클립-clip)
8. [카테고리 & 토픽 (Category & Topic)](#카테고리--토픽-category--topic)
9. [후원/도네이션 (Commercial)](#후원도네이션-commercial)
10. [기타 (Misc)](#기타-misc)
11. [재생 JSON 페이로드 상세](#재생-json-페이로드-상세)
12. [CloudStream 매핑 가이드](#cloudstream-매핑-가이드)

---

## 공통 사항

### Base URL
- `https://api.chzzk.naver.com`

### 공통 응답 봉투 (Envelope)
거의 모든 응답이 다음 형태:
```json
{
  "code": 200,
  "message": null,
  "content": { ... }
}
```
- 실패 시 `code` ≠ 200, `message`에 에러 문자열, `content` 부재 또는 null.
- 예외: `POST /service/live-status`는 `{"result_code", "result_data"}` 형식 (텔레메트리).

### 표준 요청 헤더 (PC 웹 클라이언트 흉내)
```
User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/147.0.0.0 Safari/537.36
Accept: application/json, text/plain, */*
Origin: https://chzzk.naver.com
Referer: https://chzzk.naver.com/
Front-Client-Platform-Type: PC
Front-Client-Product-Type: web
deviceId: <UUID>            # ba.uuid 쿠키와 동일값. 없으면 생성하여 양쪽에 동일 사용
Accept-Language: ko-KR,ko;q=0.9
```

### 쿠키 (인증/세션)
관전·검색·메타데이터는 비로그인으로도 대부분 호출 가능. 비로그인 시 다음만 필요:
- `ba.uuid` = 클라이언트 UUID (한 번 생성 후 유지)
- `NNB` = NAVER 비식별 쿠키 (없어도 통과되는 경우 多)

성인 콘텐츠 / 구독 전용 / 마이페이지 / 후원 작성 등에는 `NID_AUT`, `NID_SES` (NAVER 로그인 쿠키)이 필요.

### 페이지네이션 패턴
크게 세 가지:
1. **page-based**: `?page=0&size=30&pagingType=PAGE` → `content.page / size / totalCount / totalPages / data[]`
2. **offset-based**: `?offset=0&size=18` → `content.size / page.next.offset / data[]`
3. **cursor-based**: `?concurrentUserCount=N&liveId=M` → `content.page.next = { concurrentUserCount, liveId }` (다음 호출에 그대로 전달)

### `videoNo` vs `videoId`
- `videoNo`: 정수형 PK (URL 경로/검색에 사용) — 예: `12888749`
- `videoId`: 36자리 hex 문자열 (재생 정보 lookup용) — 예: `C048BAFAB2B98EE8A9A43EB3444438AC6FC3`

### `liveId` vs `channelId`
- `channelId`: 32자리 hex (채널 PK, 영구) — 예: `affa78deac0b23d2046b8ed4856c1e62`
- `liveId`: 정수형 (방송 회차 PK, 매 송출마다 증가) — 예: `18553740`

---

## 홈/탐색 (Home & Discovery)

### `GET /service/v1/topics/HOME/sub-topics/HOME/main`
홈 페이지 메인 슬롯 (CloudStream `mainPage`에 직접 매핑 가능한 핵심 엔드포인트).

**Query**
| 이름 | 필수 | 예시 | 설명 |
|---|---|---|---|
| `slotSize` | ✓ | `5` | 슬롯 콘텐츠당 노출 라이브 수 |

**Response.content**
```json
{
  "topLives": [LiveSummary, ...],
  "slots": [
    {
      "slotNo": 1065,
      "slotTitle": "LEC같이보기는 와디드님과 함께!",
      "slotContentType": "LIVE_FOCUS",
      "slotLanding": null,
      "slotContents": [LiveSummary, ...],
      "slotExtra": { "loggingId": 0 }
    }
  ],
  "remainingSlotNos": [number],
  "targetUser": null
}
```
`LiveSummary`: `liveId, liveTitle, liveImageUrl, openDate, concurrentUserCount, cvExposure, adult, categoryType, liveCategory, liveCategoryValue, watchPartyNo, watchPartyType, channel{ channelId, channelName, channelImageUrl, verifiedMark }`

---

### `GET /service/v1/topics`
사용 가능한 메인/서브 토픽 목록.

**Response.content**
```json
{ "topics": [{ "mainTopic": {"id":"HOME","name":"홈"}, "subTopics": [...], "textBubbles": [], "defaultSubTopicId": "HOME" }] }
```

---

### `GET /service/v1/streamer-partners/recommended`
파트너 스트리머 추천 캐러셀.

**Response.content.streamerPartners[]**
```
channelId, channelImageUrl, originalNickname, channelName, verifiedMark,
openLive, newStreamer, liveTitle, concurrentUserCount, cvExposure, liveCategoryValue
```

---

### `GET /service/v1/banners`
**Query**: `deviceType=PC`, `positionsIn=HOME_SCHEDULE`

**Response.content.banners[]**: `position, bannerNo, ad, imageUrl, lightThemeImageUrl, landingUrl, newWindow, title, subCopy, scheduledDate`

---

### `GET /service/v1/program-schedules/coming`
방송 예정 편성표.

**Response.content.programSchedules[]**: `seq, type, scheduleDate, scheduleTitle, channel{channelId, channelName, channelImageUrl, verifiedMark, channelDescription, openLive}`

---

### `GET /service/v1/home/skins`
홈 스킨 정보 (보통 빈 배열).

---

## 검색 (Search)

### `GET /service/v1/search/channels`
**Query**
| 이름 | 필수 | 설명 |
|---|---|---|
| `keyword` | ✓ | URL-encoded 검색어 (한글) |
| `offset` | ✓ | 시작 인덱스 (0부터) |
| `size` | ✓ | 페이지 크기 (예: 33) |
| `withFirstChannelContent` | | `true`면 첫 채널의 라이브/VOD를 함께 임베드 |

**Response.content**
```json
{
  "size": 33,
  "page": { "next": { "offset": 33 }, "prev": null },
  "data": [{
    "channel": { channelId, channelName, channelImageUrl, verifiedMark, channelDescription, followerCount, openLive, activatedChannelBadgeIds },
    "content": { "live": null|LiveDetail, "videos": [VideoSummary, ...] }
  }]
}
```

---

### `GET /service/v1/search/lives`
라이브 검색 (offset 페이지네이션). 결과 형식은 라이브 리스트와 동일 (`data[].liveId`...).

---

### `GET /service/v1/search/videos`
VOD 검색. 결과 형식은 VOD 리스트와 동일 (`data[].videoNo`...).

---

### `GET /service/v1/search/channels/auto-complete`
**Query**: `keyword`, `offset`, `size`

**Response.content**
```json
{ "page": 0, "size": 10, "totalCount": 1, "totalPages": 1, "data": ["강시나"] }
```
> `data`는 단순 string 배열.

---

## 채널 (Channel)

### `GET /service/v1/channels/{channelId}`
채널 기본 정보.

**Response.content**
```
channelId, channelName, channelImageUrl, verifiedMark, channelType,
channelDescription, followerCount, openLive,
subscriptionAvailability, subscriptionPaymentAvailability {iapAvailability, iabAvailability},
adMonetizationAvailability, activatedChannelBadgeIds[], paidProductSaleAllowed
```

---

### `GET /service/v1/channels/{channelId}/videos`
채널의 VOD 목록 (page-based 페이지네이션).

**Query**
| 이름 | 설명 |
|---|---|
| `sortType` | `LATEST` 등 |
| `pagingType` | `PAGE` |
| `page` | 0부터 |
| `size` | 페이지 크기 (16, 24, 30, 31 사용 사례 확인) |
| `publishDateAt` | 빈 문자열 가능 |
| `videoType` | 빈 문자열 가능 (모든 타입) |

**Response.content.data[]** (`VideoSummary`)
```
videoNo, videoId, videoTitle, videoType (REPLAY 등),
publishDate "YYYY-MM-DD HH:MM:SS", publishDateAt (epoch ms),
thumbnailImageUrl, trailerUrl,
duration (초), readCount, livePv,
categoryType (ETC/GAME), videoCategory, videoCategoryValue,
exposure, adult, clipActive, commentActive, chapterActive,
tags[], tvAppViewingPolicyType,
channel { channelId, channelName, channelImageUrl, verifiedMark, activatedChannelBadgeIds[] },
blindType, watchTimeline, paidProductId
```

---

### `GET /service/v1/channels/{channelId}/clips`
**Query**: `clipUID=`, `filterType=ALL`, `orderType=POPULAR|RECENT`, `page=0`, `size=30`, `readCount=` (cursor에 사용 가능)

**Response.content**
```json
{
  "size": 30,
  "page": { "next": {"clipUID": "..."} | null, "prev": null },
  "data": [{
    "clipUID", "videoId", "clipTitle",
    "ownerChannelId", "ownerChannel",
    "thumbnailImageUrl",
    "categoryType", "clipCategory",
    "duration", "adult", "createdDate", "recId", "readCount",
    "blindType", "privateUserBlock"
  }],
  "hasStreamerClips": true
}
```

---

### `GET /service/v1/channels/{channelId}/data`
**Query**: `fields=banners,topExposedVideos,missionDonationChannelHomeExposure`

**Response.content**: `topExposedVideos { openLive, fixedVideo, latestVideo: VideoSummary }`, `banners[]`, `missionDonationChannelHomeExposure`, `activatedAchievementBadgeIds[]`.

---

### `GET /service/v1/channels/{channelId}/live-recommended`
이 채널 시청자에게 추천하는 라이브.

**Response.content.recommendedContents[]**: `type ("LIVE_EXPLORE"), title, lives: LiveSummary[]`

---

### `GET /service/v1/channels/{channelId}/live-schedule`
**Response.content.schedules[]** — 보통 빈 배열, 예약 방송 시 채워짐.

---

### `GET /service/v1/channels/{channelId}/announcements`
**Response.content.followerHighRecordAnnouncement` — null 가능

---

### `GET /service/v1/channels/{channelId}/cafe-connection`
연결된 네이버 카페.
```json
{ "cafe": {"id": 1234, "name": "..."}, "menu": {"id": 1, "name": "..."} }
```

---

### `GET /service/v1/channels/{channelId}/chat-rules`
```
agree, channelId, rule, updatedDate, serviceAgree
```

---

### `GET /service/v1.1/channels/{channelId}/my-info`
> 로그인 쿠키 필요.
```
channelId, userRole, permissions, following, cheatKey, restriction, privateUserBlock,
subscription { subscribing, ... }, playerAdFlag { preRoll, midRoll, postRoll }
```

---

## 라이브 (Live)

### `GET /service/v3.3/channels/{channelId}/live-detail` ⭐
**채널의 현재 라이브 상세** — 재생 URL 추출의 메인 엔드포인트.

**Query**
| 이름 | 설명 |
|---|---|
| `cu` | `false` (concurrentUserCount 갱신 여부?) |
| `dt` | hex 토큰 (앱이 매 호출 갱신) |
| `tm` | `true`/`false` (timeMachine 활성 시) |

**Response.content**
```
liveId, liveTitle, status ("OPEN"|"CLOSED"),
liveImageUrl (thumbnail template, {type} 자리표시자),
defaultThumbnailImageUrl,
concurrentUserCount, cvExposure, accumulateCount,
openDate, closeDate, adult, krOnlyViewing, clipActive,
tags[], chatChannelId,
categoryType ("GAME"|"ETC"...), liveCategory, liveCategoryValue,
chatActive, chatAvailableGroup, paidPromotion,
chatAvailableCondition, minFollowerMinute, allowSubscriberInFollowerMode,
livePlaybackJson    ← STRINGIFIED JSON, 아래 "재생 JSON 페이로드" 참고
p2pQuality[],
timeMachineActive, timeMachinePlayback,
channel { channelId, channelName, channelImageUrl, verifiedMark, activatedChannelBadgeIds },
livePollingStatusJson,
userAdultStatus, blindType, chatDonationRankingExposure,
logPower { active, exposureWeeklyRanking, exposureMonthlyRanking },
adParameter { tag },
dropsCampaignNo, watchPartyNo, watchPartyTag, watchPartyPaidProductId, watchPartyType,
dab, earthquake, paidProduct, tvAppViewingPolicyType, party
```

**`liveImageUrl` 사용법**: `image_{type}.jpg`의 `{type}`을 `720`/`480`/`360`/`270`/`144` 중 하나로 치환.

---

### `GET /service/v1/live/{liveId}/auto-play-info`
liveId만 가지고 재생 정보를 얻을 때.
```json
{ "livePlaybackJson": "...", "livePollingStatusJson": "...", "openDate": "..." }
```

---

### `GET /polling/v3.1/channels/{channelId}/live-status`
**Query**: `includePlayerRecommendContent=true` (옵션)

**Response.content** (라이브 상태 폴링용 - `livePollingStatusJson` 안에 `callPeriodMilliSecond`로 주기 지정)
```
liveTitle, status, concurrentUserCount, cvExposure, accumulateCount,
paidPromotion, adult, krOnlyViewing, openDate, closeDate, clipActive,
chatChannelId, tags[], categoryType, liveCategory, liveCategoryValue,
livePollingStatusJson, faultStatus, userAdultStatus, abroadCountry, blindType,
playerRecommendContent { categoryLives, channelLatestVideos },
chatActive, chatAvailableGroup, chatAvailableCondition,
minFollowerMinute, allowSubscriberInFollowerMode,
chatSlowModeSec, chatEmojiMode, chatDonationRankingExposure,
dropsCampaignNo, liveTokenList, watchPartyNo, watchPartyTag, watchPartyType,
timeMachineActive, channelId, lastAdultReleaseDate, lastKrOnlyViewingReleaseDate,
lastTvAppAllowedDate, tvAppViewingPolicyType,
logPowerActive, logPowerRankingExposure,
donationCampaignId, donationBoardNo, sportsMatch, streamerShopCatalogTagActive
```

---

## VOD/다시보기 (Video)

### `GET /service/v3/videos/{videoNo}` ⭐
**VOD 상세** — 다시보기 재생 URL 추출 메인.

**Query**: `dt` (hex 토큰)

**Response.content**
```
videoNo, videoId, videoTitle, videoType ("REPLAY"),
publishDate, publishDateAt, thumbnailImageUrl, trailerUrl,
duration (초), readCount, livePv,
categoryType, videoCategory, videoCategoryValue,
exposure, adult, clipActive, commentActive, chapterActive,
tags[], tvAppViewingPolicyType,
channel { ... },
blindType, watchTimeline, paidProductId,
paidPromotion, inKey,
liveOpenDate "YYYY-MM-DD HH:MM:SS",
vodStatus ("NONE"|...),
liveRewindPlaybackJson  ← STRINGIFIED JSON, 재생 URL 포함
prevVideo, nextVideo (VideoSummary),
userAdultStatus, adParameter { tag },
videoChatEnabled, videoChatChannelId,
paidProduct, dab, streamerShopCatalogTagActive,
chapters: []
```

**404 시 응답**: `{ "code": 404, "message": "..." }` (`content` 없음)

---

### `GET /service/v1/videos/{videoNo}/live-rewind/auto-play-info`
videoNo만 가지고 재생 JSON 추출.
```json
{ "liveRewindPlaybackJson": "..." }
```

---

### `GET /service/v1/videos/{videoNo}/chats`
VOD 진행 시각별 누적 채팅.

**Query**: `playerMessageTime=0`, `previousVideoChatSize=50`

**Response.content**
```
nextPlayerMessageTime,
previousVideoChats[],
videoChats: [{
  chatChannelId, messageTime, userIdHash,
  content (메시지 텍스트),
  extras (JSON string),
  messageTypeCode, messageStatusType,
  profile (JSON string), playerMessageTime
}]
```

---

## 클립 (Clip)
채널 클립은 `[/service/v1/channels/{channelId}/clips](#get-servicev1channelschannelidclips)` 참고.

---

## 카테고리 & 토픽 (Category & Topic)

### `GET /service/v2/categories/{categoryType}/{categoryId}/lives`
**카테고리별 라이브 목록** (cursor 페이지네이션).

**경로 파라미터**
- `categoryType` ∈ `GAME`, `ETC`, ...
- `categoryId`: 예 `League_of_Legends`, `Teamfight_Tactics`, `talk`

**Query**: `size`, 또는 cursor (`concurrentUserCount=N&liveId=M`)

**Response.content**
```json
{
  "size": 2,
  "page": { "next": {"concurrentUserCount": 9999, "liveId": 18556584}, "prev": null },
  "data": [LiveSummaryFull, ...]
}
```
`LiveSummaryFull`은 `LiveSummary` + `accumulateCount, blindType` 추가.

---

### `GET /service/v1/categories/{type}/{id}/info`
카테고리 메타 (예: `/categories/ETC/talk/info`).

**Response.content**
```
categoryType, categoryId, categoryValue, posterImageUrl,
openLiveCount, concurrentUserCount,
existLounge, following, newCategory, dropsCampaignList[]
```

---

### `GET /service/v1/categories/live`
**Query**: `size=6`

**Response.content**
```json
{
  "size": 6,
  "page": { "next": { "concurrentUserCount": N, "openLiveCount": N, "categoryId": "..." }, "prev": null },
  "data": [{
    "categoryType", "categoryId", "categoryValue", "posterImageUrl",
    "openLiveCount", "concurrentUserCount", "newCategory", "dropsCampaignNos": []
  }]
}
```

---

## 후원/도네이션 (Commercial)

### `GET /commercial/v1/channels/{channelId}/donation-campaigns`
도네이션 캠페인 정보 (활성 캠페인 ID, 버튼 이미지, 프리셋 금액 등). 표시용.

### `GET /commercial/v1/channels/{channelId}/donation/rank/weekly`
**Query**: `channelId`, `withMyRank=true`, `rankCount=10`
주간 도네이터 랭킹.

### `GET /commercial/v1/streamer-shop/{channelId}/products`
**Query**: `catalogType=CATALOG`
스트리머 상점 상품 목록 (보통 비어 있음).

### `GET /commercial/v1/cheat-key/status`
구독 상태 메타 (로그인 시).

### `GET /commercial/v1/offerwall/total-reward`
**Query**: `clientPlatformType=PC`
오퍼월 누적 리워드.

### `GET /service/v1/channels/{channelId}/donations/chat-setting`
채팅 도네이션 설정.

### `GET /service/v1/channels/{channelId}/donations/video-setting`
영상 도네이션 설정.

### `GET /service/v1/channels/{channelId}/donations/mission-setting`
미션 도네이션 설정.

### `GET /service/v1/channels/{channelId}/donations/missions`
**Query**: `filterStatus=APPROVED&filterStatus=EXPIRED&page=0&size=50`
미션 목록 (page-based).

### `GET /service/v2/channels/{channelId}/donations/missions`
v1과 동일 응답, v2 경로.

### `GET /service/v1/channels/{channelId}/log-power/prediction`
방송 누적 로그 파워 예측치.

### `GET /service/v1/channels/{channelId}/log-power/rank/weekly`
주간 로그 파워 랭킹.

### `GET /service/v1/channels/{channelId}/party-donation-info`
**404 발생 사례 있음.** 파티 도네이션 미설정 채널.

---

## 기타 (Misc)

### `GET /service/v1/client-config`
클라이언트 글로벌 설정 (광고 스킵 시간, 이벤트 캠페인 등).

### `GET /service/v1/badges/assets/last-updated`
**Query**: `badgeType=CHANNEL_ACHIEVEMENT`
배지 에셋 마지막 갱신일.

### `GET /service/v2/nickname/color/codes`
닉네임 색상 코드 (channelOwner, manager 등).

### `GET /service/v1/paid-product/init`
유료 상품 활성화 여부.

### `GET /service/v1/ad/display-status`
**Query**: `pgId={channelId|videoNo}&pgType=CHZZK_LIVE|CHZZK_VIDEO`
광고 노출 가능 여부.

### `OPTIONS /service/live-status` / `POST /service/live-status`
**텔레메트리/이벤트 트래킹 엔드포인트** — 클라이언트 SDK 1.7.0이 사용. CloudStream 구현엔 불필요.

**POST Body**
```json
{
  "client": {
    "service_id": "nng", "user_key": "__UNKNOWN__",
    "os_ver": "...", "app_ver": "-1", "country": "KR", "language": "ko",
    "product": "web", "os_name": "windows",
    "device_id": "<UUID>", "device_model": "__UNKNOWN__",
    "client_context": { "sdk_ver": "1.7.0" }
  },
  "events": [{
    "event_time": <epoch_ms>,
    "scene_id": "chzzk_home",
    "action_id": "scene_enter",
    "classifier": "chzzk_home",
    "extra": {},
    "event_context": { "referrer": "" }
  }]
}
```

**Response**: `{ "result_code": 200, "result_data": { "message": "..." } }`

---

## 재생 JSON 페이로드 상세

`live-detail` / `auto-play-info` / `videos/{n}` 응답 안의 `livePlaybackJson` 및 `liveRewindPlaybackJson`은 **문자열로 직렬화된 JSON**입니다. CloudStream에서는 `JSONObject(string)` 또는 `parseJson(string)` 으로 한 번 더 파싱해야 합니다.

### `livePlaybackJson` (라이브 송출)
```jsonc
{
  "meta": {
    "videoId": "625608934...",
    "streamSeq": 26441751,
    "liveId": "18553740",
    "paidLive": false,
    "cdnInfo": { "cdnType": "LCDN" },
    "p2p": true,
    "cmcdEnabled": false,
    "playbackAuthType": "NONE"
  },
  "serviceMeta": { ... },
  "live": {
    "start": "2026-04-25T19:49:02",
    "open":  "2026-04-25T19:49:02",
    "timeMachine": false,
    "status": "STARTED"
  },
  "api": [{
    "name": "p2p-config",
    "path": "https://apis.naver.com/livecloud/livecloud/xray/p2p/v1/config/chzzk"
  }],
  "media": [
    {
      "mediaId": "HLS",       // 일반 HLS
      "protocol": "HLS",
      "path": "https://livecloud.pstatic.net/.../ry3...hls_playlist.m3u8?hdnts=...",
      "encodingTrack": [ EncodingTrack, ... ]
    },
    {
      "mediaId": "LLHLS",     // 저지연 HLS
      "protocol": "HLS",
      "path": "https://livecloud.pstatic.net/.../ry3...playlist.m3u8?hdnts=...",
      "encodingTrack": [ ... ]
    }
  ],
  "multiview": [],
  "thumbnail": {
    "snapshotThumbnailTemplate": "https://livecloud-thumb.akamaized.net/.../image_{type}.jpg",
    "spriteSeekingThumbnail": {
      "spriteFormat": { "rowCount": 3, "columnCount": 4, "intervalType": "millisecond", "interval": 20000, "thumbnailWidth": 160, "thumbnailHeight": 90 },
      "urlTemplate": "https://.../image_4x3x20000_18553740_{spriteIndex}.jpg",
      "processingSeconds": 120
    },
    "types": ["720", "480", "360", "270", "144"]
  }
}
```

### `EncodingTrack`
```jsonc
{
  "encodingTrackId": "1080p",     // 또는 "720p", "480p", "360p", "144p", "audioOnly"
  "videoProfile": "high",          // high|main
  "audioProfile": "LC",
  "p2pPath": "/chzzk/.../...m3u8?channel_id=...&cdn_url=<base64>&...",
  "p2pPathUrlEncoding": "...",
  "videoCodec": "H264",
  "videoBitRate": 6144000,
  "audioBitRate": 192000,
  "videoFrameRate": "60.0",
  "videoWidth": 1920,
  "videoHeight": 1080,
  "audioSamplingRate": 48000,
  "audioChannel": 2,
  "avoidReencoding": true,
  "videoDynamicRange": "SDR"
}
```
`encodingTrackId == "audioOnly"`인 항목은 `videoWidth/Height/BitRate`이 모두 `null`.

### `liveRewindPlaybackJson` (다시보기/타임머신)
```jsonc
{
  "meta": {
    "videoId": "C048BAFA...",
    "streamSeq": 26835044,
    "liveId": "18535884",
    "paidLive": false,
    "cdnInfo": { "cdnType": "GCDN" },
    "cmcdEnabled": false,
    "liveRewind": true,
    "duration": 13699.138,
    "playbackAuthType": "NONE"
  },
  "media": [{
    "mediaId": "HLS",
    "protocol": "HLS",
    "path": "https://light-slit.akamaized.net/.../vod_playlist.m3u8?hdnts=...",
    "encodingTrack": [ EncodingTrack, ... ]
  }]
}
```

### `livePollingStatusJson`
```json
{ "status": "STARTED", "isPublishing": true, "playableStatus": "PLAYABLE", "trafficThrottling": -1, "callPeriodMilliSecond": 10000 }
```

### 재생 URL 구성 가이드
1. master playlist 사용 시 `media[].path`만 그대로 사용.
2. 화질 직접 선택 시 `master m3u8`을 가져와 variants에서 해상도 매칭 (Cloudstream의 `M3u8Helper`로 처리 가능).
3. `path`의 쿼리 `hdnts=st=...~exp=...` 토큰은 시간 만료가 짧음 (수 시간). 매번 신선한 응답을 받아 사용해야 함.
4. P2P가 필요 없다면 `path` 그대로 HLS 플레이어에 전달. P2P 활성화 시 `encodingTrack[].p2pPath`/`p2pPathUrlEncoding` 와 `api[].path`(p2p-config) 사용.

---

## CloudStream 매핑 가이드

| CloudStream 단계 | Chzzk 엔드포인트 |
|---|---|
| `mainPage` 섹션 | `GET /service/v1/topics/HOME/sub-topics/HOME/main?slotSize=5` (`topLives`, `slots[]`)<br>`GET /service/v1/streamer-partners/recommended` (추천 파트너)<br>`GET /service/v2/categories/{type}/{id}/lives` (카테고리별 라이브) |
| `search(query)` | `GET /service/v1/search/channels?keyword=...&offset=0&size=33`<br>+ `GET /service/v1/search/lives` / `search/videos`<br>(autocomplete: `search/channels/auto-complete`) |
| `load(channelUrl)` (라이브 채널) | `GET /service/v1/channels/{channelId}` (메타) +<br>`GET /service/v3.3/channels/{channelId}/live-detail?cu=false&dt=...&tm=true` (현재 방송) +<br>`GET /service/v1/channels/{channelId}/videos?sortType=LATEST&pagingType=PAGE&page=0&size=30` (에피소드/VOD 목록) +<br>(선택) `GET /service/v1/channels/{channelId}/clips?orderType=RECENT&size=50` |
| `load(videoUrl)` (단일 VOD) | `GET /service/v3/videos/{videoNo}?dt=...` |
| `loadLinks` (라이브) | `live-detail.content.livePlaybackJson` 파싱 → `media[].path` (HLS/LLHLS) |
| `loadLinks` (다시보기) | `videos/{n}.content.liveRewindPlaybackJson` 파싱 → `media[].path` <br>또는 `GET /service/v1/videos/{n}/live-rewind/auto-play-info` |

### 권장 호출 순서 (라이브)
1. `GET /service/v3.3/channels/{channelId}/live-detail` 으로 `status == "OPEN"` 확인 + `livePlaybackJson` 추출
2. `livePlaybackJson.media[]` 중 `mediaId == "HLS"` (또는 `LLHLS`) 선택
3. `path` URL을 `M3u8Helper.generateM3u8`에 그대로 전달

### 권장 호출 순서 (다시보기)
1. `GET /service/v3/videos/{videoNo}` (404 처리 필수 — `code != 200`)
2. `liveRewindPlaybackJson` 파싱 → `media[0].path` 사용

### 주의
- `dt` 쿼리 파라미터는 hex 4~5자리, 캡처에서는 매번 다른 값 (`245a2`, `2361d`, `22498`). 클라이언트 측에서 랜덤 생성한 값으로 보임 — 비워도 통과하는지 시도 필요.
- `tm` 쿼리는 timeMachine(되감기) 활성 채널일 때 `true`.
- 모든 응답이 `code: 200`이지만 HTTP는 200/404가 혼재. **HTTP status보다 `body.code`를 신뢰**할 것.
- 한국 IP 기반 콘텐츠가 다수 (`abroadCountry`, `krOnlyViewing` 플래그 확인).
