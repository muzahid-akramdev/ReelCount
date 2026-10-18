package dev.akram.reelcount

import dev.akram.reelcount.data.ReelRepository
import dev.akram.reelcount.data.db.DailyStatEntity
import dev.akram.reelcount.data.db.EventStamp
import dev.akram.reelcount.domain.Bounds
import dev.akram.reelcount.domain.CountPolicy
import dev.akram.reelcount.domain.DayBoundary
import dev.akram.reelcount.domain.NodeSnapshot
import dev.akram.reelcount.domain.PolicyConfig
import dev.akram.reelcount.domain.RuleEngine
import dev.akram.reelcount.domain.ScrollGate
import dev.akram.reelcount.domain.SwipeDirection
import dev.akram.reelcount.domain.WatchSample
import dev.akram.reelcount.domain.crossedMilestone
import dev.akram.reelcount.domain.underLimitStreak
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class Phase1LogicTest {
    private fun policy(backward: Boolean = false): CountPolicy =
        CountPolicy {
            PolicyConfig(
                debounceMs = 600,
                countBackward = backward,
                backwardWindowMs = 2_000,
                maxGapSeconds = 180,
            )
        }

    @Test
    fun enterCountsOnceAfterDebounce() {
        val counter = policy()
        val pending = counter.onSample(WatchSample(0, true, "a", 0))
        assertFalse(pending.counted)
        assertTrue(pending.needsConfirm)
        assertFalse(counter.confirm(599).counted)
        val counted = counter.confirm(600)
        assertTrue(counted.counted)
        assertEquals(0L, counted.estSeconds)
        assertFalse(counter.onSample(WatchSample(900, true, "a", 0)).counted)
    }

    @Test
    fun forwardAdvanceCountsTheGap() {
        val counter = policy()
        counter.onSample(WatchSample(0, true, "a", 1))
        counter.confirm(600)
        assertTrue(counter.onSample(WatchSample(2_000, true, "b", 2)).needsConfirm)
        val counted = counter.confirm(2_600)
        assertTrue(counted.counted)
        assertEquals(SwipeDirection.FORWARD, counted.direction)
        assertEquals(2L, counted.estSeconds)
    }

    @Test
    fun backwardSwipeIsIgnoredUnlessEnabled() {
        val ignored = policy()
        ignored.onSample(WatchSample(0, true, "a", 3))
        ignored.confirm(600)
        ignored.onSample(WatchSample(2_000, true, "b", 2))
        val skipped = ignored.confirm(2_600)
        assertFalse(skipped.counted)
        assertEquals(SwipeDirection.BACKWARD, skipped.direction)

        val counted = policy(backward = true)
        counted.onSample(WatchSample(0, true, "a", 3))
        counted.confirm(600)
        counted.onSample(WatchSample(2_000, true, "b", 2))
        val decision = counted.confirm(2_600)
        assertTrue(decision.counted)
        assertEquals(SwipeDirection.BACKWARD, decision.direction)
    }

    @Test
    fun leavingAndReenteringCountsAgain() {
        val counter = policy()
        counter.onSample(WatchSample(0, true, "a", 0))
        counter.confirm(600)
        counter.onSample(WatchSample(1_000, false, null, null))
        counter.onSample(WatchSample(1_500, true, "c", 0))
        assertTrue(counter.confirm(2_100).counted)
    }

    @Test
    fun watchGapIsCapped() {
        val counter = policy()
        counter.onSample(WatchSample(0, true, "a", 0))
        counter.confirm(600)
        counter.onSample(WatchSample(500_600, true, "b", 1))
        val counted = counter.confirm(501_200)
        assertTrue(counted.counted)
        assertEquals(180L, counted.estSeconds)
    }

    @Test
    fun returningHashWithinTheWindowIsBackward() {
        val counter = policy()
        counter.onSample(WatchSample(0, true, "h1", null))
        counter.confirm(600)
        counter.onSample(WatchSample(2_000, true, "h2", null, scrolled = true))
        counter.confirm(2_600)
        counter.onSample(WatchSample(3_000, true, "h1", null, scrolled = true))
        val decision = counter.confirm(3_600)
        assertEquals(SwipeDirection.BACKWARD, decision.direction)
        assertFalse(decision.counted)
    }

    @Test
    fun hashChangeWithoutAScrollDoesNotCount() {
        val counter = policy()
        counter.onSample(WatchSample(0, true, "h1", null))
        assertTrue(counter.confirm(600).counted)
        val tapped = counter.onSample(WatchSample(2_000, true, "h2", null))
        assertFalse(tapped.needsConfirm)
        assertFalse(tapped.counted)
        assertFalse(counter.confirm(4_000).counted)
    }

    @Test
    fun bundledRulesIgnoreTheFeedAndAcceptAFullscreenViewer() {
        val rules = RulesParser.parse(File("src/main/assets/rules.json").readText()).getOrThrow()
        val engine = RuleEngine()
        val instagram = rules.apps.first { it.packageName == "com.instagram.android" }
        val facebook = rules.apps.first { it.packageName == "com.facebook.katana" }
        val lite = rules.apps.first { it.packageName == "com.facebook.lite" }

        assertFalse(engine.evaluate(instagram, instagramFeed()).viewerActive)
        assertTrue(engine.evaluate(instagram, instagramReels()).viewerActive)
        assertFalse(engine.evaluate(facebook, facebookCard()).viewerActive)
        assertTrue(engine.evaluate(facebook, facebookReels()).viewerActive)
        assertFalse(engine.evaluate(lite, liteTab()).viewerActive)
        assertTrue(engine.evaluate(lite, liteReels()).viewerActive)
        assertTrue(engine.evaluate(lite, liteVideoTab()).viewerActive)
        assertTrue(engine.evaluate(lite, liteSurface()).viewerActive)
        assertFalse(engine.evaluate(lite, liteHomeFeed()).viewerActive)
        assertFalse(engine.evaluate(lite, liteStory()).viewerActive)
        assertTrue(engine.evaluate(instagram, instagramReelsTab()).viewerActive)
        assertTrue(engine.evaluate(facebook, facebookReelsTab()).viewerActive)
        assertFalse(engine.evaluate(facebook, facebookHome()).viewerActive)
    }

    @Test
    fun smallScrollsInOneGestureCountOnce() {
        val gate = ScrollGate()
        assertFalse(gate.onScroll(true, 0, 0, 4000, -1, -1, 200, 2000, 0))
        assertFalse(gate.onScroll(true, 0, 200, 4000, -1, -1, 200, 2000, 40))
        assertFalse(gate.onScroll(true, 0, 400, 4000, -1, -1, 200, 2000, 80))
        assertTrue(gate.onScroll(true, 0, 800, 4000, -1, -1, 400, 2000, 120))
        assertFalse(gate.onScroll(true, 0, 820, 4000, -1, -1, 20, 2000, 2_000))
    }

    @Test
    fun coalescedScrollJumpCountsOnce() {
        val gate = ScrollGate()
        assertFalse(gate.onScroll(true, 0, 100, 8000, -1, -1, 0, 2000, 0))
        assertTrue(gate.onScroll(true, 0, 1900, 8000, -1, -1, 24, 2000, 60))
        assertFalse(gate.onScroll(true, 0, 1920, 8000, -1, -1, 20, 2000, 120))
    }

    @Test
    fun viewPagerPageChangeCountsOncePerPage() {
        val gate = ScrollGate()
        assertFalse(gate.onScroll(true, 0, 0, 0, 4, 4, 0, 2000, 0))
        assertTrue(gate.onScroll(true, 0, 0, 0, 5, 5, 0, 2000, 50))
        assertFalse(gate.onScroll(true, 0, 40, 0, 5, 5, 40, 2000, 80))
        assertTrue(gate.onScroll(true, 0, 40, 0, 6, 6, 0, 2000, 140))
    }

    @Test
    fun movementAndTheSettlingPageAreOneReel() {
        val gate = ScrollGate()
        assertFalse(gate.onScroll(true, 0, 0, 0, 1, 1, 0, 2000, 0))
        assertTrue(gate.onScroll(true, 0, 1600, 9000, 1, 1, 30, 2000, 40))
        assertFalse(gate.onScroll(true, 0, 1800, 9000, 2, 2, 20, 2000, 90))
        assertTrue(gate.onScroll(true, 0, 1800, 9000, 3, 3, 0, 2000, 160))
    }

    @Test
    fun authorChangeIsANewReelAndFacebookPlayerOpens() {
        val rules = RulesParser.parse(File("src/main/assets/rules.json").readText()).getOrThrow()
        val engine = RuleEngine()
        val instagram = rules.apps.first { it.packageName == "com.instagram.android" }
        val facebook = rules.apps.first { it.packageName == "com.facebook.katana" }
        val first = engine.evaluate(instagram, instagramAuthor("alice"))
        val second = engine.evaluate(instagram, instagramAuthor("bob"))
        assertTrue(first.viewerActive)
        assertTrue(first.strongReelKey)
        assertTrue(second.strongReelKey)
        assertTrue(first.reelKey != null && first.reelKey != second.reelKey)
        assertEquals(first.reelKey, engine.evaluate(instagram, instagramAuthor("alice")).reelKey)
        assertTrue(engine.evaluate(facebook, facebookControls()).viewerActive)
        assertTrue(engine.evaluate(facebook, facebookSurface()).viewerActive)
        assertFalse(engine.evaluate(facebook, facebookCard()).viewerActive)
    }

    @Test
    fun viewerNeedsTheScoreAndABlockerWins() {
        val rules = RulesParser.parse(SAMPLE).getOrThrow()
        val engine = RuleEngine()
        val instagram = rules.apps.first { it.packageName == "com.instagram.android" }
        val active = engine.evaluate(instagram, reelTree(inbox = false))
        assertTrue(active.viewerActive)
        assertEquals(4, active.index)
        val blocked = engine.evaluate(instagram, reelTree(inbox = true))
        assertFalse(blocked.viewerActive)
    }

    @Test
    fun bundledRulesParse() {
        val file = File("src/main/assets/rules.json")
        val parsed = RulesParser.parse(file.readText())
        assertTrue(parsed.isSuccess)
        assertEquals(4, parsed.getOrThrow().apps.size)
    }

    @Test
    fun badSchemaIsRejected() {
        val broken = SAMPLE.replace("\"schemaVersion\": 1", "\"schemaVersion\": 9")
        assertTrue(RulesParser.parse(broken).isFailure)
    }

    @Test
    fun dayBoundaryUsesTheDeviceZone() {
        val zone = ZoneId.of("Asia/Dhaka")
        val epoch = Instant.parse("2026-10-14T18:00:00Z").toEpochMilli()
        assertEquals("2026-10-15", DayBoundary.dayOf(epoch, zone))
        assertEquals(0, DayBoundary.hourOf(epoch, zone))
    }

    @Test
    fun milestonesAndStreak() {
        assertEquals(25, crossedMilestone(0, 25, 50))
        assertEquals(50, crossedMilestone(25, 50, 50))
        assertEquals(75, crossedMilestone(50, 75, 50))
        assertNull(crossedMilestone(10, 20, 50))
        val today = LocalDate.parse("2026-10-15")
        val counts = mapOf("2026-10-15" to 10, "2026-10-14" to 10, "2026-10-13" to 80)
        assertEquals(2, underLimitStreak(counts, today, 50))
        assertEquals(0, underLimitStreak(mapOf("2026-10-15" to 50), today, 50))
    }

    @Test
    fun csvHasStatsAndEvents() {
        val zone = ZoneId.of("UTC")
        val csv =
            ReelRepository.buildCsv(
                listOf(DailyStatEntity("2026-10-15", "com.instagram.android", 3, 40)),
                listOf(EventStamp(0L, "com.instagram.android")),
                zone,
            )
        assertTrue(csv.startsWith("type,day_or_time,app,count,est_seconds"))
        assertTrue(csv.contains("stat,2026-10-15,com.instagram.android,3,40"))
        assertTrue(csv.contains("event,1970-01-01T00:00"))
        assertTrue(csv.contains(",com.instagram.android,,"))
    }

    private fun instagramAuthor(name: String): NodeSnapshot {
        val author =
            node(
                "com.instagram.android:id/clips_author_username",
                "android.widget.TextView",
                name,
                40,
                1500,
                400,
                1580,
                null,
            )
        val pager =
            node(
                "com.instagram.android:id/clips_viewer_view_pager",
                "androidx.viewpager.widget.ViewPager",
                null,
                0,
                0,
                1080,
                1920,
                null,
                listOf(author),
            )
        return node(null, "android.widget.FrameLayout", null, 0, 0, 1080, 1920, null, listOf(pager))
    }

    private fun facebookControls(): NodeSnapshot = node(null, "android.view.View", "Tap to show video controls", 0, 0, 1080, 1700, null)

    private fun facebookSurface(): NodeSnapshot = node(null, "android.view.TextureView", null, 0, 0, 1080, 1920, null)

    private fun instagramFeed(): NodeSnapshot {
        val tab = node(null, "android.widget.ImageView", "Reels", 800, 1800, 1000, 1920, null)
        val pager =
            node(
                "com.instagram.android:id/view_pager",
                "androidx.viewpager.widget.ViewPager",
                null,
                0,
                0,
                1080,
                1700,
                0,
            )
        return node("com.instagram.android:id/tab_bar", "android.widget.FrameLayout", null, 0, 0, 1080, 1920, null, listOf(pager, tab))
    }

    private fun instagramReels(): NodeSnapshot {
        val page = node(null, "android.view.View", null, 0, 0, 1080, 1920, 2)
        val pager =
            node(
                "com.instagram.android:id/clips_viewer_view_pager",
                "androidx.viewpager.widget.ViewPager",
                null,
                0,
                0,
                1080,
                1920,
                null,
                listOf(page),
            )
        return node(null, "android.widget.FrameLayout", null, 0, 0, 1080, 1920, null, listOf(pager))
    }

    private fun instagramReelsTab(): NodeSnapshot {
        val tab = node(null, "android.widget.ImageView", "Reels", 800, 1800, 1000, 1920, null, selected = true)
        val pager = node(null, "androidx.viewpager.widget.ViewPager", null, 0, 0, 1080, 1700, null)
        return node(null, "android.widget.FrameLayout", null, 0, 0, 1080, 1920, null, listOf(pager, tab))
    }

    private fun facebookReelsTab(): NodeSnapshot {
        val tab = node(null, "android.view.View", "Reels", 700, 1800, 900, 1920, null, selected = true)
        val pager = node(null, "androidx.recyclerview.widget.RecyclerView", null, 0, 0, 1080, 1750, null)
        return node(null, "android.widget.FrameLayout", null, 0, 0, 1080, 1920, null, listOf(pager, tab))
    }

    private fun facebookHome(): NodeSnapshot {
        val tab = node(null, "android.view.View", "Home", 0, 1800, 200, 1920, null, selected = true)
        val pager = node(null, "androidx.recyclerview.widget.RecyclerView", null, 0, 0, 1080, 1750, null)
        val card = node("com.facebook.katana:id/fb_reel_card", "android.widget.FrameLayout", "Reels", 0, 400, 1080, 900, null)
        return node(null, "android.widget.FrameLayout", null, 0, 0, 1080, 1920, null, listOf(pager, card, tab))
    }

    private fun facebookCard(): NodeSnapshot {
        val card = node("com.facebook.katana:id/fb_reel_card", "android.widget.FrameLayout", "Reels", 0, 500, 1080, 1200, null)
        return node(null, "android.widget.FrameLayout", null, 0, 0, 1080, 1920, null, listOf(card))
    }

    private fun facebookReels(): NodeSnapshot =
        node("com.facebook.katana:id/reels_viewer", "android.widget.FrameLayout", null, 0, 0, 1080, 1920, null)

    private fun liteTab(): NodeSnapshot {
        val tab = node(null, "android.view.View", "রিল", 0, 1800, 220, 1920, null)
        return node(null, "android.widget.FrameLayout", null, 0, 0, 1080, 1920, null, listOf(tab))
    }

    private fun liteReels(): NodeSnapshot = node(null, "android.view.View", "রিল", 0, 0, 1080, 1920, null)

    private fun liteVideoTab(): NodeSnapshot {
        val tab = node(null, "android.view.View", "ভিডিও", 200, 1800, 400, 1920, null, selected = true)
        val page = node(null, "android.webkit.WebView", null, 0, 0, 1080, 1750, null)
        return node(null, "android.widget.FrameLayout", null, 0, 0, 1080, 1920, null, listOf(page, tab))
    }

    private fun liteSurface(): NodeSnapshot {
        val surface = node(null, "android.view.SurfaceView", null, 0, 0, 1080, 1920, null)
        return node(null, "android.widget.FrameLayout", null, 0, 0, 1080, 1920, null, listOf(surface))
    }

    private fun liteHomeFeed(): NodeSnapshot {
        val web = node(null, "android.webkit.WebView", null, 0, 0, 1080, 1700, null)
        val like = node(null, "android.view.View", "Like", 900, 700, 1040, 780, null)
        val home = node(null, "android.view.View", "হোম", 0, 1800, 180, 1920, null)
        return node(null, "android.widget.FrameLayout", null, 0, 0, 1080, 1920, null, listOf(web, like, home))
    }

    private fun liteStory(): NodeSnapshot {
        val surface = node(null, "android.view.SurfaceView", null, 0, 0, 1080, 1920, null)
        val story = node(null, "android.view.View", "Story", 40, 80, 200, 160, null)
        return node(null, "android.widget.FrameLayout", null, 0, 0, 1080, 1920, null, listOf(surface, story))
    }

    private fun reelTree(inbox: Boolean): NodeSnapshot {
        val inboxNode =
            if (inbox) {
                listOf(node("com.instagram.android:id/direct_inbox", "android.view.View", null, 0, 0, 10, 10, null))
            } else {
                emptyList()
            }
        val page = node(null, "android.view.View", null, 0, 0, 1080, 1920, 4)
        val pager =
            node(
                "com.instagram.android:id/clips_viewer_view_pager",
                "androidx.viewpager.widget.ViewPager",
                null,
                0,
                0,
                1080,
                1920,
                null,
                listOf(page),
            )
        return node(
            "com.instagram.android:id/clips_viewer_container",
            "android.widget.FrameLayout",
            "Reels",
            0,
            0,
            1080,
            1920,
            null,
            listOf(pager) + inboxNode,
        )
    }

    private fun node(
        viewId: String?,
        className: String,
        description: String?,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        index: Int?,
        children: List<NodeSnapshot> = emptyList(),
        selected: Boolean = false,
    ): NodeSnapshot = NodeSnapshot(viewId, className, description, Bounds(left, top, right, bottom), index, children, selected)

    private companion object {
        val SAMPLE =
            """
            {
              "schemaVersion": 1,
              "global": {
                "debounceMs": 600,
                "countOnViewerEnter": true,
                "countBackwardSwipes": false
              },
              "apps": [
                {
                  "id": "instagram",
                  "packageName": "com.instagram.android",
                  "enabled": true,
                  "viewer": {
                    "minScore": 3,
                    "signals": [
                      {"source": "viewId", "op": "regex", "value": "(?i)clips_viewer", "score": 3}
                    ],
                    "blockers": [
                      {"source": "viewId", "op": "contains", "value": "direct_inbox"}
                    ]
                  },
                  "identity": {
                    "extractors": [
                      {
                        "kind": "pagerIndex",
                        "source": "viewId",
                        "op": "regex",
                        "value": "(?i)view_pager"
                      }
                    ]
                  }
                }
              ]
            }
            """.trimIndent()
    }
}
