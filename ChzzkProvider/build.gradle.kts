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

    // v1.2 brings the Provider Settings Fragment back, so we need resources
    // (string table, layout xml). The fragment exposes NID cookie input,
    // LLHLS toggle, and cache invalidation.
    requiresResources = true
    language = "ko"

    iconUrl = "https://ssl.pstatic.net/static/nng/glive/icon/chzzk.png"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    // Material components for the settings fragment (TextInputLayout, Switch,
    // BottomSheetDialogFragment).
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
}
