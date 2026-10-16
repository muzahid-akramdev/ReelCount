package dev.akram.reelcount.domain

import org.json.JSONObject
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

object RulesParser {
    const val SCHEMA_VERSION = 1

    fun parse(text: String): Result<RuleSet> = runCatching { parseOrThrow(text) }

    private fun parseOrThrow(text: String): RuleSet {
        val root = JSONObject(text)
        val version = root.getInt("schemaVersion")
        if (version != SCHEMA_VERSION) {
            error("unsupported schemaVersion $version")
        }
        val globalJson = root.getJSONObject("global")
        val global =
            GlobalRules(
                debounceMs = globalJson.getLong("debounceMs"),
                countOnViewerEnter = globalJson.getBoolean("countOnViewerEnter"),
                countBackwardSwipes = globalJson.getBoolean("countBackwardSwipes"),
                backwardHeuristicWindowMs = globalJson.optLong("backwardHeuristicWindowMs", 2_000),
                maxGapSeconds = globalJson.optLong("maxGapSeconds", 180),
                maxTreeDepth = globalJson.optInt("maxTreeDepth", 18),
            )
        require(global.debounceMs in 200..3_000) { "debounceMs out of range" }
        require(global.maxTreeDepth in 4..32) { "maxTreeDepth out of range" }
        val appsJson = root.getJSONArray("apps")
        require(appsJson.length() > 0) { "apps is empty" }
        val apps = ArrayList<AppRule>(appsJson.length())
        val seenPackages = HashSet<String>()
        for (i in 0 until appsJson.length()) {
            val app = parseApp(appsJson.getJSONObject(i))
            require(seenPackages.add(app.packageName)) { "duplicate package ${app.packageName}" }
            apps.add(app)
        }
        return RuleSet(version, global, apps)
    }

    private fun parseApp(json: JSONObject): AppRule {
        val packageName = json.getString("packageName")
        require(PACKAGE.matches(packageName)) { "bad packageName $packageName" }
        val id = json.getString("id")
        require(ID.matches(id)) { "bad id $id" }
        val viewerJson = json.getJSONObject("viewer")
        val signalsJson = viewerJson.getJSONArray("signals")
        require(signalsJson.length() > 0) { "signals empty for $id" }
        val signals = ArrayList<Signal>(signalsJson.length())
        for (i in 0 until signalsJson.length()) {
            val signal = signalsJson.getJSONObject(i)
            val value = signal.getString("value")
            val op = matchOp(signal.getString("op"))
            validatePattern(op, value)
            val ratio = signal.optDouble("minHeightRatio", 0.0)
            require(ratio in 0.0..1.0) { "minHeightRatio out of range" }
            signals.add(
                Signal(
                    source = matchSource(signal.getString("source")),
                    op = op,
                    value = value,
                    score = signal.getInt("score"),
                    minHeightRatio = ratio,
                ),
            )
        }
        val blockers = ArrayList<Matcher>()
        val blockersJson = viewerJson.optJSONArray("blockers")
        if (blockersJson != null) {
            for (i in 0 until blockersJson.length()) {
                val blocker = blockersJson.getJSONObject(i)
                val value = blocker.getString("value")
                val op = matchOp(blocker.getString("op"))
                validatePattern(op, value)
                blockers.add(Matcher(matchSource(blocker.getString("source")), op, value))
            }
        }
        val identityJson = json.getJSONObject("identity")
        val extractorsJson = identityJson.getJSONArray("extractors")
        require(extractorsJson.length() > 0) { "extractors empty for $id" }
        val extractors = ArrayList<Extractor>(extractorsJson.length())
        for (i in 0 until extractorsJson.length()) {
            extractors.add(parseExtractor(extractorsJson.getJSONObject(i)))
        }
        return AppRule(
            id = id,
            packageName = packageName,
            enabled = json.getBoolean("enabled"),
            viewer =
                ViewerRule(
                    minScore = viewerJson.getInt("minScore"),
                    signals = signals,
                    blockers = blockers,
                ),
            identity = IdentityRule(extractors),
        )
    }

    private fun parseExtractor(json: JSONObject): Extractor {
        val kind =
            when (json.getString("kind")) {
                "pagerIndex" -> ExtractorKind.PAGER_INDEX
                "structuralHash" -> ExtractorKind.STRUCTURAL_HASH
                else -> error("unknown extractor ${json.getString("kind")}")
            }
        val include = HashSet<String>()
        val includeJson = json.optJSONArray("include")
        if (includeJson != null) {
            for (i in 0 until includeJson.length()) {
                val name = includeJson.getString(i)
                require(name in INCLUDE) { "bad include $name" }
                include.add(name)
            }
        }
        val op = if (json.has("op")) matchOp(json.getString("op")) else null
        val value = json.optString("value", "").ifBlank { null }
        if (op != null && value != null) validatePattern(op, value)
        if (kind == ExtractorKind.PAGER_INDEX) {
            require(json.has("source") && op != null && value != null) { "pagerIndex needs source, op, value" }
        }
        if (kind == ExtractorKind.STRUCTURAL_HASH) {
            require(include.isNotEmpty()) { "structuralHash needs include" }
        }
        val source = if (json.has("source")) matchSource(json.getString("source")) else null
        return Extractor(kind, source, op, value, include)
    }

    private fun validatePattern(
        op: MatchOp,
        value: String,
    ) {
        require(value.length in 1..300) { "pattern length" }
        if (op == MatchOp.REGEX) {
            try {
                Pattern.compile(value)
            } catch (error: PatternSyntaxException) {
                throw IllegalArgumentException("bad regex $value", error)
            }
        }
    }

    private fun matchSource(raw: String): MatchSource =
        when (raw) {
            "viewId" -> MatchSource.VIEW_ID
            "className" -> MatchSource.CLASS_NAME
            "contentDescription" -> MatchSource.CONTENT_DESCRIPTION
            else -> error("bad source $raw")
        }

    private fun matchOp(raw: String): MatchOp =
        when (raw) {
            "equals" -> MatchOp.EQUALS
            "contains" -> MatchOp.CONTAINS
            "regex" -> MatchOp.REGEX
            else -> error("bad op $raw")
        }

    private val PACKAGE = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
    private val ID = Regex("^[a-z0-9_]+$")
    private val INCLUDE = setOf("viewId", "className", "boundsBucket", "contentDescriptionHash")
}
