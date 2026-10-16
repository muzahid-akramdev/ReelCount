package dev.akram.reelcount.data.rules

import android.content.Context
import dev.akram.reelcount.domain.RuleSet
import dev.akram.reelcount.domain.RulesParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class RulesRepository(
    private val context: Context,
) {
    private val overrideFile = File(context.filesDir, "rules.json")
    private val mutable = MutableStateFlow(load())
    val rules: StateFlow<RuleSet> = mutable

    fun usingImport(): Boolean = overrideFile.exists()

    fun import(text: String): Result<RuleSet> {
        val parsed = RulesParser.parse(text)
        parsed.onSuccess { ruleSet ->
            overrideFile.writeText(text)
            mutable.value = ruleSet
        }
        return parsed
    }

    fun resetToBundled() {
        overrideFile.delete()
        mutable.value = load()
    }

    private fun load(): RuleSet {
        if (overrideFile.exists()) {
            val imported = RulesParser.parse(overrideFile.readText()).getOrNull()
            if (imported != null) return imported
        }
        val bundled =
            context.assets
                .open("rules.json")
                .bufferedReader()
                .use { it.readText() }
        return RulesParser.parse(bundled).getOrThrow()
    }
}
