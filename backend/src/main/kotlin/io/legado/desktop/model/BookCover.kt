package io.legado.desktop.model

import io.legado.desktop.data.entities.BaseSource
import io.legado.desktop.data.entities.Book
import io.legado.desktop.help.CacheManager
import io.legado.desktop.help.DefaultData
import io.legado.desktop.model.analyzeRule.AnalyzeRule
import io.legado.desktop.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.desktop.model.analyzeRule.AnalyzeUrl
import io.legado.desktop.utils.GSON
import io.legado.desktop.utils.fromJsonObject
import kotlinx.coroutines.currentCoroutineContext

/**
 * 桌面版 BookCover：仅保留纯逻辑部分（封面规则配置 + 封面搜索）。
 * 原版 Glide 加载/模糊/占位（load/loadManga/loadBlur/defaultDrawable）为 Android UI 专属，前端自行处理。
 */
@Suppress("ConstPropertyName")
object BookCover {

    private const val coverRuleConfigKey = "legadoCoverRuleConfig"
    const val configFileName = "coverRule.json"

    /**
     * 加载封面
     */
    fun getCoverRule(): CoverRule {
        return getConfig() ?: DefaultData.coverRule
    }

    fun getConfig(): CoverRule? {
        return GSON.fromJsonObject<CoverRule>(CacheManager.get(coverRuleConfigKey))
            .getOrNull()
    }

    suspend fun searchCover(book: Book): String? {
        val config = getCoverRule()
        if (!config.enable || config.searchUrl.isBlank() || config.coverRule.isBlank()) {
            return null
        }
        val analyzeUrl = AnalyzeUrl(
            config.searchUrl,
            book.name,
            source = config,
            coroutineContext = currentCoroutineContext(),
            hasLoginHeader = false
        )
        val res = analyzeUrl.getStrResponseAwait()
        val analyzeRule = AnalyzeRule(book, config)
        analyzeRule.setCoroutineContext(currentCoroutineContext())
        analyzeRule.setContent(res.body)
        analyzeRule.setRedirectUrl(res.url)
        return analyzeRule.getString(config.coverRule, isUrl = true)
    }

    fun saveCoverRule(config: CoverRule) {
        val json = GSON.toJson(config)
        saveCoverRule(json)
    }

    fun saveCoverRule(json: String) {
        CacheManager.put(coverRuleConfigKey, json)
    }

    fun delCoverRule() {
        CacheManager.delete(coverRuleConfigKey)
    }

    data class CoverRule(
        var enable: Boolean = true,
        var searchUrl: String,
        var coverRule: String,
        override var concurrentRate: String? = null,
        override var loginUrl: String? = null,
        override var loginUi: String? = null,
        override var header: String? = null,
        override var jsLib: String? = null,
        override var enabledCookieJar: Boolean? = false,
    ) : BaseSource {

        override fun getTag(): String {
            return "CoverRule"
        }

        override fun getKey(): String {
            return searchUrl
        }
    }

}
