package io.legado.desktop.model

import io.legado.desktop.data.entities.BookSourcePart
import io.legado.desktop.exception.NoStackTraceException
import io.legado.desktop.help.CacheManager

object CheckSource {
    internal const val EXTRA_SESSION_ID = "checkSourceSessionId"
    internal const val EXTRA_SELECTED_SOURCES_KEY = "checkSourceSelectedSourcesKey"

    var keyword = "我的"

    //校验设置
    var timeout = CacheManager.getLong("checkSourceTimeout") ?: 180000L
    var wSourceComment = CacheManager.get("wSourceComment")?.toBoolean() ?: true
    var checkDomain = CacheManager.get("checkDomain")?.toBoolean() ?: false
    var checkSearch = CacheManager.get("checkSearch")?.toBoolean() ?: true
    var checkDiscovery = CacheManager.get("checkDiscovery")?.toBoolean() ?: true
    var checkInfo = CacheManager.get("checkInfo")?.toBoolean() ?: true
    var checkCategory = CacheManager.get("checkCategory")?.toBoolean() ?: true
    var checkContent = CacheManager.get("checkContent")?.toBoolean() ?: true
    val summary get() = upSummary()

    fun start(
        context: Any,
        sources: List<BookSourcePart>,
        sessionId: Long,
    ): String {
        // 桌面版：校验执行逻辑（原 CheckSourceService）在 T4.1 实现
        throw NoStackTraceException("桌面版书源校验尚未实现（T4.1）")
    }

    fun stop(context: Any, sessionId: Long) {
        throw NoStackTraceException("桌面版书源校验尚未实现（T4.1）")
    }

    fun resume(context: Any) {
        throw NoStackTraceException("桌面版书源校验尚未实现（T4.1）")
    }

    fun putConfig() {
        CacheManager.put("checkSourceTimeout", timeout)
        CacheManager.put("wSourceComment", wSourceComment)
        CacheManager.put("checkDomain", checkDomain)
        CacheManager.put("checkSearch", checkSearch)
        CacheManager.put("checkDiscovery", checkDiscovery)
        CacheManager.put("checkInfo", checkInfo)
        CacheManager.put("checkCategory", checkCategory)
        CacheManager.put("checkContent", checkContent)
    }

    private fun upSummary(): String {
        var checkItem = ""
        if (checkDomain) checkItem = "$checkItem ${"域名"}"
        if (checkSearch) checkItem = "$checkItem ${"搜索"}"
        if (checkDiscovery) checkItem = "$checkItem ${"发现"}"
        if (checkInfo) checkItem = "$checkItem ${"详情"}"
        if (checkCategory) checkItem = "$checkItem ${"章节列表"}"
        if (checkContent) checkItem = "$checkItem ${"正文"}"
        return "检查超时:${timeout / 1000}s\n检查项目:$checkItem"
    }
}
