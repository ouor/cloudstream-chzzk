# cloudstream-chzzk

[CloudStream 3](https://github.com/recloudstream/cloudstream) provider plugin for **NAVER 치지직 (Chzzk)**.

> **⚠️ Status: Under active development.** Not yet recommended for end-users. See [PLAN.md](PLAN.md) for the development roadmap and [API.md](API.md) for the reverse-engineered Chzzk HTTP API specification.

## Features (planned)
- [ ] Live broadcast playback (HLS / LLHLS)
- [ ] VOD (다시보기) playback with seeking
- [ ] Clip playback
- [ ] Channel browsing — current live + recent VODs
- [ ] Category browsing (LoL, TFT, Talk, ...)
- [ ] Korean keyword search across channels / lives / VODs
- [ ] Home page with featured slots and recommended streamers

## Repository URL (for CloudStream)
Once published, add this URL to CloudStream → Settings → Extensions → Add repository:
```
https://raw.githubusercontent.com/ouor/cloudstream-chzzk/builds/repo.json
```
*(URL pending first release.)*

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
