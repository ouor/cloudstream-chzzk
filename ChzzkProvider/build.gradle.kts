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
        // viewBinding intentionally disabled: the cloudstream gradle plugin's
        // make task does not bundle the Java-compiled binding classes into
        // the .cs3 dex, which causes a runtime NoClassDefFoundError when the
        // settings fragment tries to inflate via the generated binding. The
        // fragment uses findViewById instead, which is included in the
        // Kotlin compile output.
        viewBinding = false
    }
}

dependencies {
    // Material components for the settings fragment (TextInputLayout, Switch,
    // BottomSheetDialogFragment).
    implementation("com.google.android.material:material:1.12.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
}
