package dev.akram.reelcount.domain

import kotlin.math.abs
import kotlin.math.max

/**
 * One reel gesture must become one count.
 *
 * A swipe is often one accessibility event whose [scrollDeltaY] is only the last
 * few pixels — the system drops the rest. The absolute scroll position still
 * jumps by about a screen, so that jump counts. ViewPager2 also writes the
 * current page into both scroll indexes; a change of that page is a reel.
 * Catch-up of a page we already counted from movement is not a second reel.
 * A later page in the same fling is.
 */
class ScrollGate {
    private var accum = 0
    private var lastY = Int.MIN_VALUE
    private var lastAt = 0L
    private var lastPage = Int.MIN_VALUE
    private var countedPage = Int.MIN_VALUE
    private var latched = false

    fun reset() {
        accum = 0
        lastY = Int.MIN_VALUE
        lastAt = 0L
        lastPage = Int.MIN_VALUE
        countedPage = Int.MIN_VALUE
        latched = false
    }

    fun onScroll(
        isScroll: Boolean,
        scrollX: Int,
        scrollY: Int,
        maxScrollY: Int,
        fromIndex: Int,
        toIndex: Int,
        deltaY: Int,
        rootHeight: Int,
        nowMs: Long,
    ): Boolean {
        if (!isScroll || rootHeight <= 0) return false
        val horizontalOnly = scrollY == 0 && maxScrollY <= 0 && scrollX != 0 && toIndex < 0
        if (horizontalOnly) return false
        if (lastAt != 0L && nowMs - lastAt > 380) {
            accum = 0
            latched = false
            if (countedPage == Int.MIN_VALUE) countedPage = lastPage
        }
        lastAt = nowMs
        if (pageAdvanced(fromIndex, toIndex)) return true
        if (latched) {
            if (scrollY >= 0) lastY = scrollY
            return false
        }
        val threshold = (rootHeight * 0.36).toInt().coerceAtLeast(1)
        val jump = if (lastY != Int.MIN_VALUE && scrollY >= 0) abs(scrollY - lastY) else 0
        // Prefer the larger of the position jump and the reported delta. A coalesced
        // event keeps the new position but a tiny delta, which used to be ignored.
        val moved = max(jump, abs(deltaY))
        if (scrollY >= 0) lastY = scrollY
        if (moved == 0) return false
        accum += moved
        if (accum < threshold) return false
        accum = 0
        latched = true
        // Movement already counted this reel. The next page index is that same reel
        // settling, not another one. A page after that is a new reel.
        countedPage = Int.MIN_VALUE
        return true
    }

    private fun pageAdvanced(
        fromIndex: Int,
        toIndex: Int,
    ): Boolean {
        if (fromIndex < 0 || toIndex < 0) return false
        val page = toIndex
        if (lastPage == Int.MIN_VALUE) {
            lastPage = page
            countedPage = page
            return false
        }
        if (page == lastPage) return false
        lastPage = page
        if (latched && countedPage == Int.MIN_VALUE) {
            countedPage = page
            accum = 0
            return false
        }
        if (page == countedPage) return false
        countedPage = page
        latched = true
        accum = 0
        return true
    }
}
