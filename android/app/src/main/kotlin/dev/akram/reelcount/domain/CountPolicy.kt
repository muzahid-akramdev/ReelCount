package dev.akram.reelcount.domain

enum class SwipeDirection { NONE, FORWARD, BACKWARD }

data class PolicyConfig(
    val debounceMs: Long = 600,
    val countOnViewerEnter: Boolean = true,
    val countBackward: Boolean = false,
    val backwardWindowMs: Long = 2_000,
    val maxGapSeconds: Long = 180,
)

data class WatchSample(
    val elapsedMs: Long,
    val viewerActive: Boolean,
    val identity: String?,
    val index: Int?,
    val scrolled: Boolean = false,
)

data class CountDecision(
    val counted: Boolean = false,
    val estSeconds: Long = 0,
    val direction: SwipeDirection = SwipeDirection.NONE,
    val needsConfirm: Boolean = false,
)

/**
 * Counts a reel only after the same identity has stayed put for [PolicyConfig.debounceMs].
 * Swipe animations fire several accessibility events; waiting for a stable frame
 * keeps one swipe at one count. The first video of a visit is counted once the
 * viewer itself has been stable for that long.
 */
class CountPolicy(
    private val configProvider: () -> PolicyConfig,
) {
    private val config: PolicyConfig get() = configProvider()
    private var inViewer = false
    private var settlingFirst = false
    private var committedIdentity: String? = null
    private var committedIndex: Int? = null
    private var previousIdentity: String? = null
    private var previousChangeAt: Long = 0
    private var pendingIdentity: String? = null
    private var pendingIndex: Int? = null
    private var pendingSince: Long = 0
    private var pendingOpen = false
    private var anchorAt: Long = 0

    fun onSample(sample: WatchSample): CountDecision {
        if (!sample.viewerActive) {
            endSession()
            return CountDecision()
        }
        if (!inViewer) {
            beginSession(sample)
            return CountDecision(needsConfirm = settlingFirst)
        }
        if (settlingFirst) {
            return trackFirst(sample)
        }
        if (sample.identity == null || sample.identity == committedIdentity) {
            pendingOpen = false
            pendingIdentity = null
            return CountDecision()
        }
        // A structural hash flaps when a button is tapped or a frame animates.
        // Those are not a new reel. A real advance has a pager index, or it arrived with a scroll.
        if (sample.index == null && !sample.scrolled) {
            return CountDecision()
        }
        if (sample.identity != pendingIdentity) {
            pendingIdentity = sample.identity
            pendingIndex = sample.index
            pendingSince = sample.elapsedMs
            pendingOpen = true
            return CountDecision(needsConfirm = true)
        }
        if (pendingOpen && sample.elapsedMs - pendingSince >= config.debounceMs) {
            return commitPending(sample.elapsedMs)
        }
        return CountDecision(needsConfirm = pendingOpen)
    }

    fun confirm(elapsedMs: Long): CountDecision {
        if (!inViewer) return CountDecision()
        if (settlingFirst) {
            if (elapsedMs - pendingSince >= config.debounceMs) {
                return commitFirst(elapsedMs)
            }
            return CountDecision(needsConfirm = true)
        }
        if (pendingOpen && pendingIdentity != null && elapsedMs - pendingSince >= config.debounceMs) {
            return commitPending(elapsedMs)
        }
        if (pendingOpen) return CountDecision(needsConfirm = true)
        return CountDecision()
    }

    private fun beginSession(sample: WatchSample) {
        inViewer = true
        committedIdentity = sample.identity
        committedIndex = sample.index
        previousIdentity = null
        anchorAt = sample.elapsedMs
        pendingIdentity = sample.identity
        pendingIndex = sample.index
        pendingSince = sample.elapsedMs
        pendingOpen = false
        settlingFirst = config.countOnViewerEnter
    }

    private fun trackFirst(sample: WatchSample): CountDecision {
        val indexedChange = sample.index != null && sample.identity != null && sample.identity != pendingIdentity
        if (indexedChange) {
            pendingIdentity = sample.identity
            pendingIndex = sample.index
            pendingSince = sample.elapsedMs
            committedIdentity = sample.identity
            committedIndex = sample.index
        }
        if (sample.elapsedMs - pendingSince >= config.debounceMs) {
            return commitFirst(sample.elapsedMs)
        }
        return CountDecision(needsConfirm = true)
    }

    private fun commitFirst(now: Long): CountDecision {
        settlingFirst = false
        pendingOpen = false
        committedIdentity = pendingIdentity
        committedIndex = pendingIndex
        anchorAt = now
        return CountDecision(
            counted = true,
            estSeconds = 0,
            direction = SwipeDirection.FORWARD,
        )
    }

    private fun commitPending(now: Long): CountDecision {
        val identity = pendingIdentity ?: return CountDecision()
        val index = pendingIndex
        val backward = isBackward(identity, index, now)
        previousIdentity = committedIdentity
        previousChangeAt = now
        committedIdentity = identity
        committedIndex = index
        pendingOpen = false
        val direction = if (backward) SwipeDirection.BACKWARD else SwipeDirection.FORWARD
        if (backward && !config.countBackward) {
            return CountDecision(direction = direction)
        }
        val gap = ((now - anchorAt) / 1000L).coerceIn(0L, config.maxGapSeconds)
        anchorAt = now
        return CountDecision(counted = true, estSeconds = gap, direction = direction)
    }

    private fun isBackward(
        identity: String,
        index: Int?,
        now: Long,
    ): Boolean {
        val committed = committedIndex
        if (index != null && committed != null && index != committed) {
            return index < committed
        }
        return identity == previousIdentity && now - previousChangeAt <= config.backwardWindowMs
    }

    private fun endSession() {
        inViewer = false
        settlingFirst = false
        pendingOpen = false
        pendingIdentity = null
        committedIdentity = null
        committedIndex = null
        previousIdentity = null
    }
}
