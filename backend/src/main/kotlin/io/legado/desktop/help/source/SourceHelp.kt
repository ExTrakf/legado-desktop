package io.legado.desktop.help.source

import io.legado.desktop.data.appDb
import io.legado.desktop.constant.SourceType
import io.legado.desktop.data.entities.BaseSource
import io.legado.desktop.data.entities.BookSource
import io.legado.desktop.data.entities.BookSourcePart
import io.legado.desktop.data.entities.RssSource
import io.legado.desktop.help.AppCacheManager
import io.legado.desktop.help.config.SourceConfig
import io.legado.desktop.help.coroutine.Coroutine
import io.legado.desktop.utils.EncoderUtils
import io.legado.desktop.utils.NetworkUtils
import io.legado.desktop.utils.splitNotBlank
import io.legado.desktop.utils.LogUtils

object SourceHelp {

    private val list18Plus by lazy {
        try {
            return@lazy String(javaClass.getResourceAsStream("/18PlusList.txt").readBytes())
                .splitNotBlank("\n").map {
                    EncoderUtils.base64Decode(it)
                }.toHashSet()
        } catch (_: Exception) {
            return@lazy emptySet()
        }
    }

    fun getSource(key: String?): BaseSource? {
        key ?: return null
        // 桌面版无全局阅读内存态（原 ReadBook/AudioPlay/ReadManga/VideoPlay），直接查库
        return appDb.bookSourceDao.getBookSource(key)
            ?: appDb.rssSourceDao.getByKey(key)
    }

    fun getSource(key: String?, type: Int): BaseSource? {
        key ?: return null
        return when (type) {
            SourceType.book -> appDb.bookSourceDao.getBookSource(key)
            SourceType.rss -> appDb.rssSourceDao.getByKey(key)
            else -> null
        }
    }

    fun deleteSource(key: String, type: Int) {
        when (type) {
            SourceType.book -> deleteBookSource(key)
            SourceType.rss -> deleteRssSource(key)
        }
    }

    fun deleteBookSourceParts(sources: List<BookSourcePart>) {
        deleteBookSourceKeys(sources.map { it.bookSourceUrl })
    }

    fun deleteBookSources(sources: List<BookSource>) {
        deleteBookSourceKeys(sources.map { it.bookSourceUrl })
    }

    private fun deleteBookSourceKeys(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val sourceKeys = keys.distinct()
        appDb.runInTransaction {
            sourceKeys.forEach(::deleteBookSourceInternal)
        }
        SourceConfig.removeSources(sourceKeys)
        AppCacheManager.clearSourceVariables()
    }

    private fun deleteBookSourceInternal(key: String) {
        clearSharedGlobalStateBySourceKey(BookSource::class.java, key)
        appDb.bookSourceDao.delete(key)
        appDb.cacheDao.deleteSourceVariables(key)
    }

    fun deleteBookSource(key: String) {
        deleteBookSourceKeys(listOf(key))
    }

    fun deleteRssSources(sources: List<RssSource>) {
        appDb.runInTransaction {
            sources.forEach {
                deleteRssSourceInternal(it.sourceUrl)
            }
        }
        AppCacheManager.clearSourceVariables()
    }

    private fun deleteRssSourceInternal(key: String) {
        clearSharedGlobalStateBySourceKey(RssSource::class.java, key)
        appDb.rssSourceDao.delete(key)
        appDb.rssArticleDao.delete(key)
        appDb.cacheDao.deleteSourceVariables(key)
    }

    fun deleteRssSource(key: String) {
        deleteRssSourceInternal(key)
        AppCacheManager.clearSourceVariables()
    }

    fun enableSource(key: String, type: Int, enable: Boolean) {
        when (type) {
            SourceType.book -> appDb.bookSourceDao.enable(key, enable)
            SourceType.rss -> appDb.rssSourceDao.enable(key, enable)
        }
    }

    fun insertRssSource(vararg rssSources: RssSource) {
        val rssSourcesGroup = rssSources.groupBy {
            is18Plus(it.sourceUrl)
        }
        rssSourcesGroup[true]?.forEach {
            LogUtils.e("桌面版","${it.sourceName}是18+网址,禁止导入.")
        }
        rssSourcesGroup[false]?.let {
            appDb.rssSourceDao.insert(*it.toTypedArray())
        }
    }

    fun insertBookSource(vararg bookSources: BookSource) {
        val bookSourcesGroup = bookSources.groupBy {
            is18Plus(it.bookSourceUrl)
        }
        bookSourcesGroup[true]?.forEach {
            LogUtils.e("桌面版","${it.bookSourceName}是18+网址,禁止导入.")
        }
        bookSourcesGroup[false]?.let {
            appDb.bookSourceDao.insert(*it.toTypedArray())
        }
        Coroutine.async {
            adjustSortNumber()
        }
    }

    private fun is18Plus(url: String?): Boolean {
        if (list18Plus.isEmpty()) {
            return false
        }
        url ?: return false
        val baseUrl = NetworkUtils.getBaseUrl(url) ?: return false
        kotlin.runCatching {
            val host = baseUrl.split("//", ".").let {
                if (it.size > 2) "${it[it.lastIndex - 1]}.${it.last()}" else return false
            }
            return list18Plus.contains(host)
        }
        return false
    }

    /**
     * 调整排序序号
     */
    fun adjustSortNumber() {
        if (
            appDb.bookSourceDao.maxOrder > 99999
            || appDb.bookSourceDao.minOrder < -99999
            || appDb.bookSourceDao.hasDuplicateOrder
        ) {
            val sources = appDb.bookSourceDao.allPart
            sources.forEachIndexed { index, bookSource ->
                bookSource.customOrder = index
            }
            appDb.bookSourceDao.upOrder(sources)
        }
    }



}
