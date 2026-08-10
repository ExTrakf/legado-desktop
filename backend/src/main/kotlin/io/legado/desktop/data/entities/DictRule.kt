package io.legado.desktop.data.entities

import io.legado.desktop.model.analyzeRule.AnalyzeRule
import io.legado.desktop.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.desktop.model.analyzeRule.AnalyzeUrl
import kotlinx.coroutines.currentCoroutineContext

/**
 * 字典规则
 */
data class DictRule(
    var name: String = "",
    var urlRule: String = "",
    var showRule: String = "",
    var enabled: Boolean = true,
    var sortNumber: Int = 0
) {

    override fun hashCode(): Int {
        return name.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is DictRule) {
            return name == other.name
        }
        return false
    }

    /**
     * 搜索字典
     */
    suspend fun search(word: String): String {
        val analyzeUrl = AnalyzeUrl(urlRule, key = word, coroutineContext = currentCoroutineContext())
        val body = analyzeUrl.getStrResponseAwait().body
        if (showRule.isBlank()) {
            return body!!
        }
        val analyzeRule = AnalyzeRule().setCoroutineContext(currentCoroutineContext())
        analyzeRule.setRuleName(name)
        return analyzeRule.getString(showRule, mContent = body)
    }

    suspend fun buttonClick(name: String, click: String) {
        val analyzeRule = AnalyzeRule().setCoroutineContext(currentCoroutineContext())
        analyzeRule.setRuleName(this.name)
        analyzeRule.evalJS(click , name)
    }

}