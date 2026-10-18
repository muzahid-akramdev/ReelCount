package dev.akram.reelcount.domain

import kotlin.math.abs

/**
 * A reel swipe is often many small scroll deltas, not one page-sized event.
 * Deltas in one gesture add up. A pause clears the pile, so a slow comments
 * drag does not become a count.
 */
class ScrollGate {
    private var accum = 0
    private var lastY = Int.MIN_VALUE
    private var lastAt = 0L

    fun reset() {
        accum = 0
        lastY = Int.MIN_VALUE
        lastAt = 0L
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
        if (toIndex >= 0 && toIndex != fromIndex) {
            reset()
            return true
        }
        val threshold = (rootHeight * 0.42).toInt().coerceAtLeast(1)
        if (lastAt != 0L && nowMs - lastAt > 900) accum = 0
        lastAt = nowMs
        val moved =
            when {
                deltaY != 0 -> abs(deltaY)
                lastY != Int.MIN_VALUE -> abs(scrollY - lastY)
                scrollY >= threshold -> scrollY
                else -> 0
            }
        if (scrollY >= 0) lastY = scrollY
        accum += moved
        if (accum < threshold) return false
        accum = 0
        return true
    }
}
