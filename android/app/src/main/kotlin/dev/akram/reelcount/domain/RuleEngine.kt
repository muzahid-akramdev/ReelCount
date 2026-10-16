package dev.akram.reelcount.domain

import java.util.regex.Pattern

class RuleEngine {
    fun evaluate(
        rule: AppRule,
        root: NodeSnapshot,
    ): Evaluation {
        if (rule.viewer.blockers.any { blocker -> treeAny(root) { matches(blocker, it) } }) {
            return Evaluation(viewerActive = false, identity = null, index = null)
        }
        var score = 0
        val used = HashSet<Signal>()
        walk(root) { node ->
            for (signal in rule.viewer.signals) {
                if (signal !in used && matches(signal, node)) {
                    used.add(signal)
                    score += signal.score
                }
            }
        }
        if (score < rule.viewer.minScore) {
            return Evaluation(viewerActive = false, identity = null, index = null)
        }
        val focus = bestFocus(rule, root)
        val (identity, index) = identityOf(rule.identity, focus, root)
        return Evaluation(viewerActive = true, identity = identity, index = index)
    }

    private fun bestFocus(
        rule: AppRule,
        root: NodeSnapshot,
    ): NodeSnapshot {
        var best = root
        var bestScore = 0
        var bestArea = -1
        walk(root) { node ->
            var nodeScore = 0
            for (signal in rule.viewer.signals) {
                if (matches(signal, node)) nodeScore += signal.score
            }
            val area = node.bounds.width * node.bounds.height
            if (nodeScore > 0 && (nodeScore > bestScore || (nodeScore == bestScore && area > bestArea))) {
                best = node
                bestScore = nodeScore
                bestArea = area
            }
        }
        return best
    }

    private fun identityOf(
        identity: IdentityRule,
        focus: NodeSnapshot,
        root: NodeSnapshot,
    ): Pair<String?, Int?> {
        for (extractor in identity.extractors) {
            when (extractor.kind) {
                ExtractorKind.PAGER_INDEX -> {
                    val index = pagerIndex(extractor, focus) ?: pagerIndex(extractor, root)
                    if (index != null) return "i$index" to index
                }
                ExtractorKind.STRUCTURAL_HASH -> {
                    val hash = structuralHash(focus, extractor.include)
                    if (hash != null) return hash to null
                }
            }
        }
        return null to null
    }

    private fun pagerIndex(
        extractor: Extractor,
        root: NodeSnapshot,
    ): Int? {
        val source = extractor.source ?: return null
        val op = extractor.op ?: return null
        val value = extractor.value ?: return null
        val matcher = Matcher(source, op, value)
        var bestIndex: Int? = null
        var bestArea = -1
        walk(root) { node ->
            if (!matches(matcher, node)) return@walk
            val index = deepestIndex(node) ?: return@walk
            val area = node.bounds.width * node.bounds.height
            if (area >= bestArea) {
                bestArea = area
                bestIndex = index
            }
        }
        return bestIndex
    }

    private fun deepestIndex(node: NodeSnapshot): Int? {
        var found = node.collectionIndex
        var area = if (found != null) node.bounds.width * node.bounds.height else -1
        walk(node) { child ->
            val index = child.collectionIndex ?: return@walk
            val childArea = child.bounds.width * child.bounds.height
            if (childArea >= area) {
                area = childArea
                found = index
            }
        }
        return found
    }

    private fun structuralHash(
        root: NodeSnapshot,
        include: Set<String>,
    ): String? {
        if (include.isEmpty()) return null
        val builder = StringBuilder()
        walk(root) { node ->
            if ("viewId" in include) builder.append(node.viewId ?: "").append('|')
            if ("className" in include) builder.append(node.className ?: "").append('|')
            if ("boundsBucket" in include) builder.append(node.bounds.bucket()).append('|')
            if ("contentDescriptionHash" in include) {
                val description = node.contentDescription
                if (!description.isNullOrBlank()) {
                    builder.append(description.hashCode()).append('|')
                }
            }
            builder.append(';')
        }
        if (builder.length < 8) return null
        val folded =
            builder
                .toString()
                .hashCode()
                .toUInt()
                .toString(16)
        return "h$folded"
    }
}

internal fun treeAny(
    root: NodeSnapshot,
    predicate: (NodeSnapshot) -> Boolean,
): Boolean {
    if (predicate(root)) return true
    return root.children.any { treeAny(it, predicate) }
}

internal fun walk(
    root: NodeSnapshot,
    visit: (NodeSnapshot) -> Unit,
) {
    visit(root)
    root.children.forEach { walk(it, visit) }
}

internal fun matches(
    matcher: Matcher,
    node: NodeSnapshot,
): Boolean = matches(matcher.source, matcher.op, matcher.value, node)

internal fun matches(
    signal: Signal,
    node: NodeSnapshot,
): Boolean = matches(signal.source, signal.op, signal.value, node)

private fun matches(
    source: MatchSource,
    op: MatchOp,
    value: String,
    node: NodeSnapshot,
): Boolean {
    val raw =
        when (source) {
            MatchSource.VIEW_ID -> node.viewId
            MatchSource.CLASS_NAME -> node.className
            MatchSource.CONTENT_DESCRIPTION -> node.contentDescription
        } ?: return false
    return when (op) {
        MatchOp.EQUALS -> raw == value
        MatchOp.CONTAINS -> raw.contains(value)
        MatchOp.REGEX -> regex(value).matcher(raw).find()
    }
}

private val regexCache = HashMap<String, Pattern>()

private fun regex(value: String): Pattern = regexCache.getOrPut(value) { Pattern.compile(value) }
