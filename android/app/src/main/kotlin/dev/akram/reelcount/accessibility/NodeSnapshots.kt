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
            lines.add("$indent$id | $cls$index | $desc")
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
            for (index in 0 until count) {
                if (budget[0] <= 0) break
                val child = node.getChild(index) ?: continue
                try {
                    val snap = captureNode(child, depth - 1, budget)
                    if (snap != null) children.add(snap)
                } finally {
                    child.recycle()
                }
            }
        }
        val description = node.contentDescription?.toString()?.take(180)
        val info = node.collectionItemInfo
        val index =
            when {
                info == null -> null
                info.rowIndex == 0 && info.columnIndex > 0 -> info.columnIndex
                else -> info.rowIndex
            }
        return NodeSnapshot(
            viewId = node.viewIdResourceName,
            className = node.className?.toString(),
            contentDescription = description,
            bounds = Bounds(rect.left, rect.top, rect.right, rect.bottom),
            collectionIndex = index,
            children = children,
        )
    }
}
