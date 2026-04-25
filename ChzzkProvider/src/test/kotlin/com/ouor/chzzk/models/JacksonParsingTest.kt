package com.ouor.chzzk.models

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests for Jackson deserialization of captured Chzzk responses.
 * Each fixture is a verbatim sample from the API capture set; if the upstream
 * schema changes in a way that breaks one of our DTOs, these tests are the
 * first place it shows up.
 *
 * Fixtures live under src/test/resources/fixtures/.
 */
class JacksonParsingTest {
    private val mapper: ObjectMapper = ObjectMapper().registerModule(kotlinModule())

    private fun loadFixture(name: String): String {
        val classLoader = checkNotNull(javaClass.classLoader) { "no class loader" }
        val stream = checkNotNull(classLoader.getResourceAsStream("fixtures/$name")) {
            "Missing test fixture: fixtures/$name"
        }
        return stream.bufferedReader().use { it.readText() }
    }

    @Test fun `live-detail parses with playback json string preserved`() {
        val json = loadFixture("live-detail.json")
        val res: ChzzkResponse<LiveDetail> = mapper.readValue(json)
        assertEquals(200, res.code)
        val detail = res.content
        assertNotNull("live-detail content must not be null", detail)
        detail!!
        assertNotNull(detail.liveTitle)
        assertNotNull(detail.channel.channelId)
        assertEquals("OPEN", detail.status)
        // playback JSON should arrive as a raw string for second-stage parsing
        val playbackJson = detail.livePlaybackJson
        assertNotNull("livePlaybackJson must not be null", playbackJson)
        assertTrue("livePlaybackJson should look like JSON", playbackJson!!.startsWith("{"))
        // round-trip parse the inner playback payload
        val playback: LivePlayback = mapper.readValue(playbackJson)
        assertFalse("media list should be non-empty for an OPEN live", playback.media.isEmpty())
        val hls = playback.media.firstOrNull { it.mediaId == "HLS" }
        assertNotNull("expected an HLS media entry", hls)
        assertTrue("encoding tracks should be present", (hls?.encodingTrack?.size ?: 0) > 0)
    }

    @Test fun `video-detail parses with rewind playback json`() {
        val json = loadFixture("video-detail.json")
        val res: ChzzkResponse<VideoDetail> = mapper.readValue(json)
        assertEquals(200, res.code)
        val detail = res.content!!
        assertEquals("REPLAY", detail.videoType)
        assertNotNull(detail.liveRewindPlaybackJson)
        // prev/next graph
        assertNotNull("nextVideo expected", detail.nextVideo)
    }

    @Test fun `home-main parses topLives and slots`() {
        val json = loadFixture("home-main.json")
        val res: ChzzkResponse<HomeMain> = mapper.readValue(json)
        assertEquals(200, res.code)
        val home = res.content!!
        assertFalse(home.topLives.isEmpty())
        assertFalse(home.slots.isEmpty())
        // verify slot content shape
        val firstSlot = home.slots.first()
        assertNotNull(firstSlot.slotTitle)
    }

    @Test fun `channel-videos page-data parses with totalPages`() {
        val json = loadFixture("channel-videos.json")
        val res: ChzzkResponse<PageData<VideoSummary>> = mapper.readValue(json)
        assertEquals(200, res.code)
        val page = res.content!!
        assertTrue(page.totalPages != null && page.totalPages!! >= 1)
        assertFalse(page.data.isEmpty())
    }

    @Test fun `live-detail with NVELOP cdn variant parses identically`() {
        // Live HLS host can flip between livecloud.pstatic.net (LCDN) and
        // nvelop-livecloud.pstatic.net (NVELOP) depending on CDN routing.
        // The schema is identical — this regression test pins both.
        val json = loadFixture("live-detail-nvelop.json")
        val res: ChzzkResponse<LiveDetail> = mapper.readValue(json)
        assertEquals(200, res.code)
        val detail = res.content!!
        assertEquals("OPEN", detail.status)
        val playback: LivePlayback = mapper.readValue(detail.livePlaybackJson!!)
        val hls = playback.media.first { it.mediaId == "HLS" }
        assertTrue(
            "Expected NVELOP host on this fixture",
            hls.path.contains("nvelop-livecloud.pstatic.net"),
        )
    }

    @Test fun `clip-detail parses with optionalProperty channels`() {
        val json = loadFixture("clip-detail.json")
        val res: ChzzkResponse<ClipDetail> = mapper.readValue(json)
        assertEquals(200, res.code)
        val clip = res.content!!
        assertEquals("ABR_HLS", clip.vodStatus)
        assertNotNull(clip.videoId)
        assertNotNull(clip.optionalProperty?.ownerChannel?.channelId)
        // makerChannel can differ from ownerChannel when a fan made the clip
        assertNotNull(clip.optionalProperty?.makerChannel?.channelId)
    }

    @Test fun `clip play-info parses MPD baseURLs for both mp4 and hls variants`() {
        // Verifies the api-videohub.naver.com response shape that powers
        // clip playback. Real fixture from a 2026-04-26 capture of clip
        // RpukCCV0vA — the MPD tree carries one progressive MP4 variant
        // (mimeType=video/mp4) and one HLS variant (mimeType=video/mp2t),
        // each with a single Representation that has a direct BaseURL.
        val json = loadFixture("clip-play-info.json")
        val env: ShortformCardEnvelope = mapper.readValue(json)
        val vod = env.card?.content?.vod
        assertNotNull("vod node missing", vod)
        assertTrue("vod must be playable in fixture", vod!!.playable)
        val sets = vod.playback?.mpd?.firstOrNull()?.period?.firstOrNull()?.adaptationSet.orEmpty()
        assertEquals("expected exactly two AdaptationSets (mp4 + hls)", 2, sets.size)
        val mp4Set = sets.first { it.mimeType == "video/mp4" }
        val mp4Url = mp4Set.representation.firstOrNull()?.baseUrl?.firstOrNull()
        assertNotNull(mp4Url)
        assertTrue(
            "mp4 BaseURL should target the b02-kr-smp-vod CDN",
            mp4Url!!.startsWith("https://b02-kr-smp-vod.pstatic.net/glive-clip/"),
        )
        assertTrue("mp4 BaseURL must include hdnts auth token", mp4Url.contains("hdnts="))
        // Clip is a portrait shortform — width 720, height 1280. The "720P"
        // in the qualityId label refers to the shorter dimension.
        val rep = mp4Set.representation.first()
        assertEquals("720", rep.width)
        assertEquals("1280", rep.height)
    }

    @Test fun `category-lives parses with cursor next pointer`() {
        val json = loadFixture("category-lives.json")
        val res: ChzzkResponse<PageData<LiveSummary>> = mapper.readValue(json)
        assertEquals(200, res.code)
        val page = res.content!!
        assertFalse(page.data.isEmpty())
        // cursor is a Map<String, Any?> exposed through PageData.pageCursor
        val nextCursor = page.pageCursor?.next
        assertNotNull("expected page.next cursor for category lives", nextCursor)
        assertNotNull(nextCursor!!["concurrentUserCount"])
        assertNotNull(nextCursor["liveId"])
    }
}
