version = 2

cloudstream {
    description = "네이버 치지직 라이브 / 다시보기 / 클립"
    authors = listOf("ouor")

    /**
     * Status int as one of the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta-only
     */
    status = 3

    tvTypes = listOf("Live", "Movie")

    // The settings fragment is built programmatically in Kotlin (no XML, no
    // R class lookup) so resources are not needed. Earlier attempts with
    // `requiresResources = true` + R.layout.* crashed at runtime because
    // the cloudstream gradle plugin's `make` task doesn't bundle the
    // javac-produced R$layout / R$id classes into the .cs3 dex.
    requiresResources = false
    language = "ko"

    iconUrl = "https://ssl.pstatic.net/static/nng/glive/icon/chzzk.png"
}

// android.buildFeatures intentionally not customised — the cloudstream
// gradle plugin only ships the Kotlin compile output to the .cs3 dex, so any
// javac-only feature (viewBinding, dataBinding, R class lookups) crashes at
// runtime with NoClassDefFoundError. The settings fragment is built entirely
// in Kotlin views to stay inside that compile graph.

dependencies {
    // BottomSheetDialogFragment lives in Material — we still need it as a
    // compile-time dep, but we deliberately do not use any Material widgets
    // that require theme attributes the host activity may not provide.
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
}
