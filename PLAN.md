# Chzzk Plugin 개발 작업 계획

CloudStream 3용 치지직(Chzzk) provider 플러그인 개발 로드맵. [API.md](API.md)의 명세를 기반으로 작성.

## 0. 목표 정의

### 지원 콘텐츠
- ✅ **라이브 방송** (현재 송출 중)
- ✅ **다시보기 (REPLAY VOD)**
- ✅ **클립**
- ⏸️ 채팅 / 도네이션 / 멀티뷰 (1차 범위 외)

### CloudStream `TvType` 매핑
| Chzzk 콘텐츠 | TvType | 비고 |
|---|---|---|
| 라이브 방송 (`status: OPEN`) | `Live` | `LiveStreamLoadResponse` 사용 |
| 다시보기 (`videoType: REPLAY`) | `Movie` 또는 `TvSeries` | 채널 단위로 묶을지 단일로 다룰지는 §2 결정 |
| 클립 (60초 내외) | `Movie` | 짧은 영상이라 단일 entity |

---

## 1. 프로젝트 부트스트랩 (1일차)

**목표:** 빈 골격을 빌드 통과시키기.

- [ ] **1.1** `ExampleProvider/` → `ChzzkProvider/`로 디렉토리 리네이밍
- [ ] **1.2** 패키지 `com.example` → `com.ouor.chzzk`로 일괄 변경
  - `AndroidManifest.xml`, `*.kt`, `build.gradle.kts`의 `namespace`
- [ ] **1.3** 클래스 리네이밍: `ExampleProvider` → `ChzzkProvider`, `ExamplePlugin` → `ChzzkPlugin`
- [ ] **1.4** `ChzzkProvider/build.gradle.kts` 메타데이터 작성
  ```kotlin
  description = "네이버 치지직 라이브/다시보기 스트리밍"
  authors = listOf("ouor")
  status = 1
  tvTypes = listOf("Live", "Movie")
  language = "ko"
  iconUrl = "https://chzzk.naver.com/favicon.ico"
  ```
- [ ] **1.5** `requiresResources = false` 로 변경 (1차에선 fragment UI 미사용)
  - `BlankFragment.kt` 및 `res/` 삭제
  - `ChzzkPlugin.load()`에서 `openSettings` 제거
- [ ] **1.6** `./gradlew :ChzzkProvider:assembleDebug` 빌드 통과 확인
- [ ] **1.7** GitHub Actions secrets/permissions 확인 (Read & write, all actions enabled)
- [ ] **1.8** README.md를 fork에 맞게 업데이트 (저장소 URL 등)

**산출물:** 빌드 가능한 빈 ChzzkProvider, push 시 builds 브랜치에 .cs3 산출.

---

## 2. URL 스키마 & 데이터 모델 (1일차 후반)

**목표:** CloudStream의 ID URL을 Chzzk 식별자로 매핑하는 규칙 확정.

### 2.1 URL 스키마 결정
CloudStream의 `LoadResponse.url`과 `SearchResponse.url`로 사용할 내부 URL 형식. Chzzk의 실제 URL을 그대로 쓰는 것이 자연스럽고 깊은 링크에도 유리.

| 콘텐츠 | URL 형식 |
|---|---|
| 채널 (라이브/VOD 묶음) | `https://chzzk.naver.com/{channelId}` |
| 라이브 단일 | `https://chzzk.naver.com/live/{channelId}` |
| VOD | `https://chzzk.naver.com/video/{videoNo}` |
| 클립 | `https://chzzk.naver.com/clips/{clipUID}` |

### 2.2 Jackson 데이터 클래스
응답 envelope + 도메인 모델을 별도 파일로 분리.

```
ChzzkProvider/src/main/kotlin/com/ouor/chzzk/
├── ChzzkPlugin.kt
├── ChzzkProvider.kt
├── api/
│   ├── ChzzkApi.kt          // app.get/post 래퍼, 공통 헤더
│   └── Endpoints.kt         // URL 상수
└── models/
    ├── Envelope.kt          // ChzzkResponse<T> { code, message, content }
    ├── Channel.kt           // ChannelInfo
    ├── Live.kt              // LiveDetail, LiveSummary, LivePlayback
    ├── Video.kt             // VideoDetail, VideoSummary
    ├── Clip.kt
    └── Search.kt
```

`@JsonIgnoreProperties(ignoreUnknown = true)` 일괄 적용. nullable 필드는 `?` 처리.

### 2.3 핵심 모델 스케치
```kotlin
data class ChzzkResponse<T>(
    val code: Int,
    val message: String?,
    val content: T?
)

data class LiveDetail(
    val liveId: Long,
    val liveTitle: String,
    val status: String,             // "OPEN" | "CLOSED"
    val liveImageUrl: String?,      // {type} 자리표시자 포함
    val openDate: String?,
    val closeDate: String?,
    val concurrentUserCount: Int,
    val adult: Boolean,
    val tags: List<String>?,
    val categoryType: String?,
    val liveCategory: String?,
    val liveCategoryValue: String?,
    val livePlaybackJson: String,   // 한 번 더 파싱
    val channel: ChannelInfo,
)

data class LivePlayback(
    val meta: PlaybackMeta,
    val media: List<PlaybackMedia>,
)
data class PlaybackMedia(
    val mediaId: String,            // "HLS" | "LLHLS"
    val protocol: String,
    val path: String,
    val encodingTrack: List<EncodingTrack>?,
)
```

---

## 3. `mainPage` 구현 (2일차) ⭐

**목표:** 홈 화면에 인기 라이브/카테고리/추천 표시.

### 3.1 Provider 설정
```kotlin
override val mainPage = mainPageOf(
    "TOP_LIVES" to "인기 라이브",
    "RECOMMENDED" to "추천 채널",
    "GAME/League_of_Legends" to "리그 오브 레전드",
    "GAME/Teamfight_Tactics" to "전략적 팀 전투",
    "ETC/talk" to "잡담",
    // 필요에 따라 카테고리 추가
)
```

### 3.2 분기 처리
- `TOP_LIVES` / 첫 진입: `GET /service/v1/topics/HOME/sub-topics/HOME/main?slotSize=5`
  - `topLives[]`에서 `LiveSummary` 리스트 추출 → `LiveSearchResponse`
- `RECOMMENDED`: `GET /service/v1/streamer-partners/recommended`
- `GAME/...`, `ETC/...`: `GET /service/v2/categories/{type}/{id}/lives`
  - 페이지네이션 cursor (`concurrentUserCount`, `liveId`) 보존 → `MainPageRequest.data`로 다음 페이지 호출 가능

### 3.3 LiveSearchResponse 변환
```kotlin
private fun LiveSummary.toSearchResponse() = newLiveSearchResponse(
    name = "${channel.channelName} · $liveTitle",
    url = "$mainUrl/live/${channel.channelId}",
    type = TvType.Live
) {
    posterUrl = liveImageUrl?.replace("{type}", "720")
    apiName = this@ChzzkProvider.name
}
```

**완료 기준:** 홈 화면에 최소 3개 섹션이 라이브 썸네일과 함께 출력됨.

---

## 4. `search` 구현 (2일차)

**목표:** 키워드로 채널/라이브/VOD 통합 검색.

### 4.1 전략
3개 엔드포인트를 병렬 호출 후 결합 (NiceHttp `parallelMap` 사용 가능):
1. `GET /service/v1/search/channels?keyword=...&offset=0&size=20`
2. `GET /service/v1/search/lives?keyword=...&offset=0&size=20`
3. `GET /service/v1/search/videos?keyword=...&offset=0&size=20`

### 4.2 결과 우선순위
- 라이브 진행 중인 채널 (`channel.openLive == true`) 먼저
- 라이브 검색 결과
- VOD 검색 결과
- 라이브 안 켠 채널

### 4.3 `quickSearch` (옵션)
`GET /service/v1/search/channels/auto-complete` — 단순 string list 반환이라 빠름. CloudStream의 `quickSearch`에 매핑.

**완료 기준:** "강시나" 검색 시 채널/라이브/VOD가 적절히 노출.

---

## 5. `load` 구현 (3-4일차) ⭐

**목표:** 콘텐츠 상세 페이지 데이터 조립.

### 5.1 URL 라우팅
```kotlin
override suspend fun load(url: String): LoadResponse? = when {
    "/live/" in url   -> loadLive(extractChannelId(url))
    "/video/" in url  -> loadVideo(extractVideoNo(url))
    "/clips/" in url  -> loadClip(extractClipUID(url))
    else              -> loadChannel(extractChannelId(url))   // 채널 묶음
}
```

### 5.2 채널 라우트 (`loadChannel`)
**병렬 호출:**
- `GET /service/v1/channels/{id}` — 채널 메타
- `GET /service/v3.3/channels/{id}/live-detail?cu=false&dt={hex}&tm=true` — 현재 라이브
- `GET /service/v1/channels/{id}/videos?sortType=LATEST&pagingType=PAGE&page=0&size=30`

**반환 형식:** `TvSeriesLoadResponse` (각 VOD를 episode로)
- 라이브 진행 중이면 첫 episode를 LIVE로 추가
- `name = channelName`, `posterUrl = channelImageUrl`, `plot = channelDescription`
- `tags = ["치지직", liveCategoryValue]`

### 5.3 라이브 라우트 (`loadLive`)
- `GET /service/v3.3/channels/{id}/live-detail`
- `LiveStreamLoadResponse` 반환
  - `dataUrl = livePlaybackJson` 자체를 직접 넣지 않고, 직렬화된 식별자 + JSON을 dataUrl JSON으로 묶어 `loadLinks`에서 재파싱

### 5.4 VOD 라우트 (`loadVideo`)
- `GET /service/v3/videos/{videoNo}?dt={hex}`
- `vodStatus != "NONE"` 체크 (만료된 다시보기 처리)
- `MovieLoadResponse` 반환
  - `dataUrl`에 `liveRewindPlaybackJson` 또는 videoNo

### 5.5 클립 라우트 (`loadClip`)
> ⚠️ 캡처에 클립 재생 엔드포인트가 빠져 있음. 다음 후보를 사전 조사 필요:
> - `GET /service/v1/play-info/clip/{clipUID}` (추측)
> - 클립 페이지 HTML scraping (`https://chzzk.naver.com/clips/{clipUID}`)
> - 1차 출시에선 클립 비활성 후 추후 추가 가능

### 5.6 헬퍼: `dt` 파라미터 생성
```kotlin
private fun randomDt(): String = (0..0xFFFFF).random().toString(16)
```

---

## 6. `loadLinks` 구현 (4일차) ⭐⭐

**목표:** HLS 재생 URL 추출 → 플레이어로 전달.

### 6.1 라이브
1. `loadLinks(data)`에서 data를 다시 파싱 → `livePlaybackJson` 획득
2. `media[]` 중 `mediaId == "HLS"` (또는 `LLHLS`도 별도 옵션) 선택
3. `M3u8Helper.generateM3u8(name, path, referer = "https://chzzk.naver.com/")` 호출
4. `callback(ExtractorLink)` 로 콜백

### 6.2 다시보기
1. `liveRewindPlaybackJson` 파싱
2. `media[0].path` HLS master playlist
3. 동일하게 `M3u8Helper.generateM3u8` 사용

### 6.3 자막
캡처 데이터엔 자막 트랙 미발견. 1차 출시 미지원, 추후 master m3u8 안의 `EXT-X-MEDIA TYPE=SUBTITLES` 파싱하여 `SubtitleFile` 추가 가능.

### 6.4 검증 체크리스트
- [ ] `hdnts` 토큰 만료 확인 (수 시간 단위)
- [ ] LLHLS vs HLS 둘 다 재생되는지
- [ ] 1080p/720p/480p variant가 master에 포함되는지
- [ ] 다시보기 30분 영상 정상 시킹

---

## 7. 에러 처리 & 견고성 (5일차)

- [ ] **7.1** `ChzzkApi`에 envelope 검사 헬퍼: `code != 200`이면 `ErrorLoadingException(message)`
- [ ] **7.2** 404 (`/service/v3/videos/{n}` 만료) 분기: friendly 메시지
- [ ] **7.3** 성인 콘텐츠 (`adult: true`) 처리 — 비로그인 시 차단
- [ ] **7.4** Region-locked (`krOnlyViewing: true`) 안내
- [ ] **7.5** 모든 nullable 응답에 `*OrNull` 사용
- [ ] **7.6** Jackson 미스매치 방지: `@JsonIgnoreProperties(ignoreUnknown = true)`
- [ ] **7.7** `app.get` 타임아웃 / 재시도 정책 (NiceHttp 기본값으로 충분한지 확인)

---

## 8. 페이지네이션 & UX 개선 (6일차)

- [ ] **8.1** 카테고리 라이브 `cursor` 기반 다음 페이지 (`MainPageRequest.data`에 cursor 직렬화)
- [ ] **8.2** 채널 VOD page-based 페이지네이션 (`page=N`)
- [ ] **8.3** 검색 offset 페이지네이션 (`offset=N`)
- [ ] **8.4** `LiveSummary.concurrentUserCount`를 부제로 노출 (`12,345명 시청 중`)
- [ ] **8.5** `verifiedMark`인 채널은 이름 옆에 ✓ 표시 (subname)

---

## 9. (선택) 로그인 지원

- [ ] **9.1** `LoginAPI` 인터페이스 구현 — `NID_AUT`, `NID_SES` 쿠키 입력 받기
- [ ] **9.2** 로그인 시 마이 채널 / 팔로잉 라이브 노출
- [ ] **9.3** 19금 콘텐츠 접근 (성인 인증된 계정 한정)
- [ ] **9.4** 본 캡처에 로그인 흐름 데이터 부족 — 추가 캡처 필요

> 1차 출시 범위에서 제외 권장.

---

## 10. 테스트 & 출시 (7일차)

### 10.1 수동 회귀 테스트 (CloudStream 디버그 빌드)
- [ ] 홈 화면 모든 섹션 로드
- [ ] "강시나", "랄로" 등 한글 검색
- [ ] 라이브 방송 재생 (1080p, 720p)
- [ ] 다시보기 재생 + 시킹
- [ ] 클립 재생 (구현 시)
- [ ] 카테고리 → 라이브 진입
- [ ] 페이지네이션 (스크롤)
- [ ] 만료된 VOD 에러 메시지

### 10.2 자동화
- [ ] CI에서 `./gradlew assembleDebug` 통과
- [ ] (선택) `ProviderTester` 등 CloudStream 헬퍼로 스모크 테스트

### 10.3 배포
- [ ] `version` bump (`build.gradle.kts`)
- [ ] master에 push → builds 브랜치에 .cs3 자동 생성
- [ ] JSON 저장소 (`repo.json`) 작성 — [API.md](API.md) §3 참고
- [ ] README에 CloudStream에 추가하는 URL 안내

---

## 11. 마일스톤 요약

| 마일스톤 | 완료 기준 | 예상 |
|---|---|---|
| **M0** Bootstrap | 빌드 통과, builds 브랜치 산출 | 1일 |
| **M1** Discovery MVP | mainPage + search 동작 | +2일 |
| **M2** Playback MVP | 라이브 + VOD 재생 성공 | +2일 |
| **M3** Hardening | 에러 처리, 페이지네이션, 검증 | +1일 |
| **M4** Release v1.0 | repo.json 배포 | +1일 |

**총 예상 기간: ~7일 (단독 개발 기준).**

---

## 12. 미해결 질문 / 사전조사 필요

1. **클립 재생 URL** 엔드포인트 — 본 캡처에 없음. 클립 페이지에서 재캡처 필요.
2. **`dt` 파라미터** 생성 알고리즘 — 빈 값/임의 값으로도 통과하는지 직접 호출 검증.
3. **저지연 LLHLS** vs 일반 HLS — CloudStream의 ExoPlayer가 LLHLS를 잘 다루는지.
4. **인증 게이팅** — 19금/구독 전용 콘텐츠가 비로그인 호출에서 어떤 에러를 내는지.
5. **WatchParty / 멀티뷰** — `watchPartyType`, `multiview[]`이 별도 재생 흐름을 요구하는지.
6. **광고 / 사전 광고** — `playerAdFlag.preRoll` true인 라이브에서 HLS 직접 재생 시 광고 우회 여부.

이 질문들은 §1~§6 구현 중 자연스럽게 해결됨. 필요시 추가 트래픽 캡처를 진행.
