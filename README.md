# cloudstream-chzzk

[CloudStream 3](https://github.com/recloudstream/cloudstream) provider plugin for **NAVER 치지직 (Chzzk)**.

> **⚠️ Status: Under active development.** Not yet recommended for end-users. See [PLAN.md](PLAN.md) for the development roadmap and [API.md](API.md) for the reverse-engineered Chzzk HTTP API specification.

## Features
- [x] Live broadcast playback (HLS / LLHLS)
- [x] VOD (다시보기) playback with seeking
- [ ] Clip playback (endpoint TBD — see [PLAN.md](PLAN.md) §12)
- [x] Channel browsing — current live + recent VODs as a TvSeries
- [x] Category browsing (LoL, TFT, Valorant, OW2, Talk)
- [x] Korean keyword search across channels / lives / VODs
- [x] Home page with featured slots and recommended partner streamers
- [ ] Login (19+ / subscriber-only content) — not in v1

## Adding to CloudStream
Add this repository URL in CloudStream → Settings → Extensions → Add repository:
```
https://raw.githubusercontent.com/ouor/cloudstream-chzzk/master/repo.json
```
The repo manifest points at the auto-generated `plugins.json` on the `builds` branch, which is updated by GitHub Actions after every push to `master`.

## Building locally
```
./gradlew ChzzkProvider:make
# or deploy directly to a connected device
./gradlew ChzzkProvider:deployWithAdb
```

## Granting "All Files Access" on Android 11+
Required for local plugin testing.

**Via ADB**
```
adb shell appops set --uid PACKAGE_NAME MANAGE_EXTERNAL_STORAGE allow
```
Replace `PACKAGE_NAME` with the CloudStream variant you use:
- debug: `com.lagradost.cloudstream3.prerelease.debug`
- prerelease: `com.lagradost.cloudstream3.prerelease`
- stable: `com.lagradost.cloudstream3`

**Manually**: Settings → Apps → Special app access → All files access → enable for the CloudStream variant.

## License
Public domain (matches the upstream TestPlugins template).

## Attribution
- Built on the [recloudstream/TestPlugins](https://github.com/recloudstream/TestPlugins) template.
- Plugin system heavily based on [Aliucord](https://github.com/Aliucord).
