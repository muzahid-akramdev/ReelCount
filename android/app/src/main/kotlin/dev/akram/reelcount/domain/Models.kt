package dev.akram.reelcount.domain

import java.time.LocalDate

data class Bounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    fun bucket(): String {
        val bw = width / 40
        val bh = height / 40
        val bt = top / 40
        return "$bw:$bh:$bt"
    }
}

/**
 * A privacy-trimmed accessibility node. [contentDescription] is held only in
 * memory so rules and the debug inspector can see it. It is never written to
 * Room or logcat.
 */
data class NodeSnapshot(
    val viewId: String?,
    val className: String?,
    val contentDescription: String?,
    val bounds: Bounds,
    val collectionIndex: Int?,
    val children: List<NodeSnapshot>,
    val selected: Boolean = false,
)

enum class MatchSource { VIEW_ID, CLASS_NAME, CONTENT_DESCRIPTION }

enum class MatchOp { EQUALS, CONTAINS, REGEX }

enum class MatchRegion { ANY, BOTTOM }

data class Matcher(
    val source: MatchSource,
    val op: MatchOp,
    val value: String,
    val region: MatchRegion = MatchRegion.ANY,
)

data class Signal(
    val source: MatchSource,
    val op: MatchOp,
    val value: String,
    val score: Int,
    val minHeightRatio: Double = 0.0,
    val requireSelected: Boolean = false,
)

enum class ExtractorKind { PAGER_INDEX, STRUCTURAL_HASH }

data class Extractor(
    val kind: ExtractorKind,
    val source: MatchSource? = null,
    val op: MatchOp? = null,
    val value: String? = null,
    val include: Set<String> = emptySet(),
)

data class ViewerRule(
    val minScore: Int,
    val signals: List<Signal>,
    val blockers: List<Matcher>,
)

data class IdentityRule(
    val extractors: List<Extractor>,
)

data class AppRule(
    val id: String,
    val packageName: String,
    val enabled: Boolean,
    val viewer: ViewerRule,
    val identity: IdentityRule,
)

data class GlobalRules(
    val debounceMs: Long,
    val countOnViewerEnter: Boolean,
    val countBackwardSwipes: Boolean,
    val backwardHeuristicWindowMs: Long,
    val maxGapSeconds: Long,
    val maxTreeDepth: Int,
)

data class RuleSet(
    val schemaVersion: Int,
    val global: GlobalRules,
    val apps: List<AppRule>,
)

data class Evaluation(
    val viewerActive: Boolean,
    val identity: String?,
    val index: Int?,
    val reelKey: String? = null,
    val strongReelKey: Boolean = false,
)

enum class LimitBand { GREEN, AMBER, RED }

fun limitBand(
    count: Int,
    limit: Int,
): LimitBand {
    if (limit <= 0) return LimitBand.GREEN
    val ratio = count.toFloat() / limit.toFloat()
    return when {
        ratio < 0.5f -> LimitBand.GREEN
        ratio < 1f -> LimitBand.AMBER
        else -> LimitBand.RED
    }
}

fun crossedMilestone(
    before: Int,
    after: Int,
    limit: Int,
): Int? {
    if (after <= before || limit <= 0) return null
    val points = ArrayList<Int>(4)
    val half = (limit / 2).coerceAtLeast(1)
    points.add(half)
    if (limit != half) points.add(limit)
    var extra = limit + 25
    while (extra <= after) {
        points.add(extra)
        extra += 25
    }
    return points.lastOrNull { it in (before + 1)..after }
}

fun underLimitStreak(
    countsByDay: Map<String, Int>,
    today: LocalDate,
    limit: Int,
): Int {
    if (countsByDay.isEmpty()) return 0
    var streak = 0
    var cursor = today
    while (streak < 3660) {
        val count = countsByDay[cursor.toString()]
        if (count == null) {
            if (cursor == today) {
                streak += 1
                cursor = cursor.minusDays(1)
                continue
            }
            break
        }
        if (count >= limit) break
        streak += 1
        cursor = cursor.minusDays(1)
    }
    return streak
}
