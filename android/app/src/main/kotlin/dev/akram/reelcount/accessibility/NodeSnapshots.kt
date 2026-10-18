package dev.akram.reelcount.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import dev.akram.reelcount.domain.Bounds
import dev.akram.reelcount.domain.NodeSnapshot

object NodeSnapshots {
    fun capture(
        root: AccessibilityNodeInfo,
        maxDepth: Int,
        maxNodes: Int = 280,
    ): NodeSnapshot? {
        val budget = intArrayOf(maxNodes)
        return captureNode(root, maxDepth, budget)
    }

    fun flatten(
        root: NodeSnapshot,
        limit: Int = 80,
    ): List<String> {
        val lines = ArrayList<String>(limit)

        fun walk(
            node: NodeSnapshot,
            depth: Int,
        ) {
            if (lines.size >= limit) return
            val indent = "  ".repeat(depth.coerceAtMost(8))
            val id = node.viewId ?: "-"
            val cls = node.className?.substringAfterLast('.') ?: "-"
            val desc = node.contentDescription?.take(120) ?: ""
            val index = node.collectionIndex?.let { " #$it" }.orEmpty()
            val selected = if (node.selected) " [selected]" else ""
            lines.add("$indent$id | $cls$index$selected | $desc")
            node.children.forEach { walk(it, depth + 1) }
        }
        walk(root, 0)
        return lines
    }

    private fun captureNode(
        node: AccessibilityNodeInfo,
        depth: Int,
        budget: IntArray,
    ): NodeSnapshot? {
        if (budget[0] <= 0) return null
        budget[0] -= 1
        val rect = Rect()
        node.getBoundsInScreen(rect)
        val children = ArrayList<NodeSnapshot>()
        if (depth > 0) {
            val count = node.childCount
            val ordered = ArrayList<AccessibilityNodeInfo>(count)
            for (index in 0 until count) {
                val child = node.getChild(index) ?: continue
                ordered.add(child)
            }
            // The fullscreen viewer is the large child. Walk it before chrome so a
            // node budget cannot drop the reel that is actually on screen.
            ordered.sortByDescending { child ->
                val area = Rect()
                child.getBoundsInScreen(area)
                area.width().toLong() * area.height().toLong()
            }
            try {
                for (child in ordered) {
                    if (budget[0] <= 0) break
                    val snap = captureNode(child, depth - 1, budget)
                    if (snap != null) children.add(snap)
                }
            } finally {
                ordered.forEach { it.recycle() }
            }
        }
        val spoken =
            node.contentDescription
                ?.toString()
                ?.trim()
                ?.take(180)
                ?.ifBlank { null }
        val typed = node.text?.toString()?.trim()
        val description = spoken ?: typed?.takeIf { it.length in 1..48 && !it.contains('\n') }
        val info = node.collectionItemInfo
        val itemIndex =
            when {
                info == null -> null
                info.rowIndex == 0 && info.columnIndex > 0 -> info.columnIndex
                else -> info.rowIndex
            }
        val state = if (android.os.Build.VERSION.SDK_INT >= 30) node.stateDescription?.toString() else null
        val selected =
            node.isSelected ||
                node.isChecked ||
                state?.contains("select", ignoreCase = true) == true ||
                state?.contains("নির্বাচিত") == true ||
                description?.contains("selected", ignoreCase = true) == true ||
                description?.contains("নির্বাচিত") == true
        return NodeSnapshot(
            viewId = node.viewIdResourceName,
            className = node.className?.toString(),
            contentDescription = description,
            bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
            collectionIndex = itemIndex,
            children = children,
            selected = selected,
        )
    }
}
