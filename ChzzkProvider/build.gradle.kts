version = 1

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

    requiresResources = false
    language = "ko"

    iconUrl = "https://ssl.pstatic.net/static/nng/glive/icon/chzzk.png"
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
}
