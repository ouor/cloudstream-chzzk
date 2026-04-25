# Chzzk Plugin 개발 계획

CloudStream 3용 치지직(Chzzk) provider 플러그인 개발 로드맵. v1.0 기능은 [README.md](README.md)에 정리되어 있으며, API 명세는 [API.md](API.md)에 있다.

---

## 1. 현재 상태 (v1.0 — 완료)

| 마일스톤 | 커밋 | 내용 |
|---|---|---|
| **M0** Bootstrap | [88f063d](88f063d) | `ExampleProvider` → `ChzzkProvider` 리네이밍, `com.ouor.chzzk` 패키지, fragment UI 제거, gradle 메타데이터 |
| **M0.5** Scaffold | [7aa5d0b](7aa5d0b) | `ChzzkApi`, `Endpoints`, Jackson DTO 9종, `Urls` 라우터 |
| **M1** Discovery | [71124e6](71124e6) | `mainPage` (홈/파트너/카테고리 7섹션) + 통합 `search` |
| **M2** Playback | [e569797](e569797) | `load` (live/video/clip/channel) + `loadLinks` (HLS 추출) |
| **M3** Hardening | [be61367](be61367) | 성인/blindType 가드, 시청자 수 표시, 인증 마크 |
| **M4** Release | [4d4bfd6](4d4bfd6) | `repo.json` 매니페스트, README 설치 가이드 |

### v1.0이 지원하는 것
- 라이브 / VOD HLS 재생 (master playlist 기반)
- 채널 단위 묶음 (현재 라이브 + 최근 30개 VOD를 TvSeries로)
- 5개 인기 카테고리 + 홈 + 파트너 스트리머
- 한글 통합 검색 (channels/lives/videos 병합 + 중복 제거)
- 성인 / blindType 자동 차단

### v1.0이 미지원
- 클립 재생 (엔드포인트 미캡처)
- 페이지네이션 (모든 리스트 첫 페이지만)
- 로그인 / 19+ / 구독 콘텐츠
- 채팅 / 도네이션 / 멀티뷰 / WatchParty / 타임머신
- 화질 직접 선택 (master m3u8에 위임)

---

## 2. 다음 작업: 그룹별 Sprint 계획

각 그룹은 **한 PR / 한 스프린트**로 처리할 단위. 코드 영역, 테스트 범위, 외부 의존성을 기준으로 묶었다.

### 🅰️ Group A — 화질 & 재생 옵션
**범위:** `ChzzkProvider.kt::emitMediaLinks`, `models/Live.kt`
- 수동 화질 선택 (`encodingTrack[]` 별 ExtractorLink 분리)
- audioOnly 트랙 별도 emit
- LLHLS vs HLS 토글 (settings에서)
- 광고 우회 명시 (`playerAdFlag` 무시 명세화)

**완료 기준:** 1080p/720p/480p/360p/144p/audio-only 6개 변형이 플레이어 메뉴에 표시.

---

### 🅱️ Group B — 페이지네이션 일괄
**범위:** `Endpoints.kt`, `ChzzkProvider.kt::getMainPage|search|loadChannel`
- 검색 `offset` 페이지네이션
- 카테고리 cursor (`concurrentUserCount`+`liveId`) 보존
- 채널 VOD `page=N`
- 클립 `clipUID` cursor

**핵심 작업:** `MainPageRequest.data`에 cursor 직렬화하는 헬퍼(`CursorCodec`) 신설 — 4개 패턴이 같은 코드를 공유.

**완료 기준:** 검색/카테고리/VOD 무한 스크롤 정상.

---

### 🅲 Group C — mainPage 콘텐츠 다양화
**범위:** `ChzzkProvider.kt::mainPage|getMainPage`, `Endpoints.kt`
- 카테고리 동적 로딩 (`/categories/live`)
- 편성표 섹션 (`program-schedules/coming`)
- 홈 배너 (`banners`)
- 태그 기반 탐색 (검색 결과 활용)

**완료 기준:** 홈 화면 섹션 8+ 개, 카테고리 하드코딩 제거.

---

### 🅳 Group D — load 페이지 풍성화
**범위:** `ChzzkProvider.kt::loadChannel|loadVideo`
- 채널 추천 (`live-recommended` → `LoadResponse.recommendations`)
- Prev/Next VOD 시리즈 묶기 (`prevVideo`/`nextVideo` 그래프)
- 비디오 챕터 (`chapters[]`) — 시킹 마커
- VOD 자동 다음편 큐
- (Group M 흡수) Sprite seeking 썸네일, trailer 미리보기

**완료 기준:** VOD 끝나면 자동으로 다음 편 재생. 채널 페이지 하단에 추천 라이브 표시.

---

### 🅴 Group E — 클립 지원 (사전조사 + 구현)
**범위:** 추가 캡처 → `Endpoints.clip*`, `loadClip`, 클립 `loadLinks` 분기

**사전조사:**
1. `https://chzzk.naver.com/clips/{clipUID}` 페이지 네트워크 트래픽 캡처
2. 재생 URL 엔드포인트 식별 (예상: `service/v1/play-info/clip/{uid}` 또는 유사)
3. HLS / MP4 / progressive download 여부 확인

**완료 기준:** 검색에서 노출된 클립 클릭 시 재생.

---

### 🅵 Group F — 검색 응답성
**범위:** `ChzzkProvider.kt::quickSearch`, `Endpoints.kt`
- `quickSearch`를 autocomplete API로 분리 (현재 `search()` 재사용)
- 시청자 수 / 카테고리 정렬 옵션
- 검색 필터 (라이브 only / VOD only)

**완료 기준:** 키 입력 즉시 자동완성 나옴 (현재는 무거운 통합 검색이 발화).

---

### 🅶 Group G — 로그인 & 개인화
**범위:** 신규 `auth/`, `LoginAPI` 구현, `ChzzkApi` 쿠키 주입
- 로그인 (`NID_AUT`/`NID_SES` 쿠키 입력)
- 팔로잉 라이브 mainPage 섹션
- 시청 기록 (`watchTimeline`)
- 19+ 콘텐츠 접근 (성인 인증 계정)
- 구독자 전용 콘텐츠

**사전조사:** 로그인 후 트래픽 한 세트 추가 캡처 필요. 만료/리프레시 동작 확인.

**완료 기준:** 사용자가 NID 쿠키 입력 후 팔로잉 섹션과 19+ 콘텐츠 접근.

---

### 🅷 Group H — 채팅 & 도네이션 (Fragment UI 부활)
**범위:** 신규 `chat/`, fragment 복원, `requiresResources=true`
- VOD 채팅 오버레이 (`videos/{n}/chats`)
- 라이브 채팅 WebSocket (`chatChannelId` → `chat.chzzk.naver.com`)
- 도네이션 랭킹 표시
- 채팅 룰 안내
- 닉네임 색상 (`nickname/color/codes`)

**전제:** CloudStream extension API가 라이브 채팅 오버레이를 허용하는지 확인 필요. 없으면 Provider Settings 안의 별도 화면으로 제공.

**완료 기준:** 라이브 시청 중 우측 패널에 채팅 표시.

---

### 🅸 Group I — 라이브 부가 기능
**범위:** `ChzzkProvider.kt::loadLive|loadLinks`, `models/Live.kt`
- WatchParty 섹션 (`watchPartyType=NORMAL`)
- 멀티뷰 (`livePlaybackJson.multiview[]`)
- 타임머신 되감기 (`tm=true`, `timeMachineActive`)

**완료 기준:** 같이보기 라이브가 별도 mainPage 섹션. 타임머신 활성 라이브에 "되감기 시작" 옵션.

---

### 🅹 Group J — 부가 메타데이터
**범위:** `loadChannel`, `loadLive`의 plot/tags
- 방송 알림 (CloudStream notification API)
- 카페 연동 표시 (`cafe-connection`)
- 로그파워 랭킹 (`log-power/rank/weekly`)
- 스트리머 상점 (`streamer-shop/{id}/products`)

**완료 기준:** 채널 페이지에 카페 링크와 팬덤 메트릭이 noticeably 노출.

---

### 🅺 Group K — 인프라 견고화
**범위:** `ChzzkApi.kt`, `build.gradle.kts`, `strings.xml`
- Provider Settings UI (fragment 부활) — Group H와 동시 가능
- 재시도 / Backoff (`ChzzkApi.get` 래퍼 강화)
- 응답 캐시 (메모리 LRU, 채널 메타 5분)
- Telemetry opt-in (`POST /service/live-status`)
- 다국어 (한국어/영어 `strings.xml`)
- `ba.uuid` SharedPreferences 영속화

**완료 기준:** 401/429 시 자동 재시도. 같은 채널 30초 내 재진입 시 네트워크 호출 없음.

---

### 🅻 Group L — 리버스 & 검증
**범위:** 외부 조사 + 작은 패치들
- `dt` 파라미터 정확한 알고리즘 리버스 (Chrome JS 분석)
- Region 우회/안내 (`krOnlyViewing=true` 메시지)
- CI Gradle 캐시 추가 (`.github/workflows/build.yml`)
- Unit test (Jackson 파싱 회귀 — 캡처된 응답 fixture 활용)

**완료 기준:** PR 빌드 시간 단축, 응답 스키마 변경에 대한 회귀 알람.

---

## 3. 작업 순서 (Phase)

```
Phase 1 — 기능 확장:           A → B → C
Phase 2 — load 풍성:           D (+M 흡수)
Phase 3 — 외부 의존 작업:       E (클립) → G (로그인)
Phase 4 — 커뮤니티:             H (+I 함께)
Phase 5 — 잔여 UX:              F, J
Phase 6 — 인프라/품질:          K, L
```

### 병렬 가능 조합
- **A + B** — 코드 영역 분리 (재생 vs 페이지네이션)
- **K + H** — Group H가 fragment 부활을 시작하면 K의 settings UI 작업과 합쳐 한 번에
- **L** — 단독 / 자투리 시간

### 권장 시작점: Phase 1 (Group A → B → C)
v1.0의 가장 큰 약점은 **30개 이상 결과를 못 보고**, **화질 선택이 안 된다**는 것. Phase 1을 끝내면 사용자 체감이 가장 크게 변함.

---

## 4. 마일스톤 후보

| 버전 | 포함 그룹 | 핵심 가치 |
|---|---|---|
| v1.1 | A, B | 화질 선택 + 무한 스크롤 |
| v1.2 | C, D | 홈 다양화 + VOD 시리즈 경험 |
| v1.3 | E | 클립 재생 |
| v2.0 | G | 로그인 / 19+ / 팔로잉 |
| v2.1 | H, I | 채팅 / WatchParty / 타임머신 |
| v2.2 | F, J | 자동완성 / 부가 메타 |
| v2.3 | K, L | 인프라 견고화 |

각 버전은 `ChzzkProvider/build.gradle.kts`의 `version` 정수를 1씩 증가.

---

## 5. 미해결 질문

1. **클립 재생 엔드포인트** — Group E 사전조사 (트래픽 캡처).
2. **`dt` 파라미터** — 빈 값/임의 hex/정확한 알고리즘? Group L에서 검증.
3. **LLHLS** — CloudStream ExoPlayer가 저지연 HLS의 `EXT-X-PART` 등을 처리하는지. Group A에서 실측.
4. **CloudStream 채팅 오버레이** — extension에서 지원하는 표준 API가 있는지, 아니면 Provider Settings 화면으로 우회해야 하는지. Group H 시작 전 조사.
5. **로그인 후 트래픽** — Group G 시작 전 NID 쿠키 보유 상태로 추가 캡처 필요.
6. **자막 / 청각장애인 자막** — master m3u8 안에 `EXT-X-MEDIA TYPE=SUBTITLES` 포함 여부. Group A 부수 확인.
7. **WatchParty 재생 흐름** — 일반 라이브와 동일한 `livePlaybackJson`인지 별도 인증 흐름인지. Group I 시작 전 캡처.

---

## 6. 작업 원칙

- 각 그룹 완료 시 한 commit (또는 작은 PR), 메시지 prefix는 그룹 코드 (`feat(A): ...`, `feat(B): ...`).
- 모든 신규 파일은 `com.ouor.chzzk` 하위 적절한 서브패키지 (`api/`, `models/`, `auth/`, `chat/`).
- Jackson DTO는 `@JsonIgnoreProperties(ignoreUnknown = true)` 필수, nullable 필드는 `?`.
- `app.get`/`app.post` 직접 호출 금지 — 항상 `ChzzkApi`를 통해서.
- 새 엔드포인트 추가 시 [API.md](API.md)에 동시 반영.
- `--no-verify`, `--force-with-lease` 등 git 안전장치 비활성화 금지.
