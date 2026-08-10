package io.legado.desktop.data.dao.impl

import io.legado.desktop.data.SqlExecutor.execute
import io.legado.desktop.data.SqlExecutor.queryList
import io.legado.desktop.data.SqlExecutor.queryOne
import io.legado.desktop.data.SqlExecutor.queryValue
import io.legado.desktop.data.SqliteDatabase
import io.legado.desktop.data.dao.RssSourceDao
import io.legado.desktop.data.entities.RssSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** RssSourceDao SQLite 实现（SQL 对照 Legado Room @Query） */
class RssSourceDaoImpl : RssSourceDao {

    private val db get() = SqliteDatabase.get()
    private val lock get() = SqliteDatabase.dbLock

    private fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    override fun getByKey(key: String): RssSource? = withLock {
        db.queryOne("select * from rssSources where sourceUrl = ?", listOf(key), RssSource::class.java)
    }

    override fun getRssSources(vararg sourceUrls: String): List<RssSource> = withLock {
        db.queryList("select * from rssSources where sourceUrl in (:sourceUrls)", listOf(sourceUrls.toList()), RssSource::class.java)
    }

    override fun findExistingSourceUrls(sourceUrls: List<String>): List<String> = withLock {
        db.queryList("select sourceUrl from rssSources where sourceUrl in (:sourceUrls)", listOf(sourceUrls), String::class.java)
    }

    override val all: List<RssSource> get() = withLock {
        db.queryList("SELECT * FROM rssSources order by customOrder", emptyList(), RssSource::class.java)
    }

    override val size: Int get() = withLock {
        db.queryValue("select count(sourceUrl) from rssSources", emptyList(), Int::class.java) ?: 0
    }

    override fun flowAll(): Flow<List<RssSource>> = flow {
        emit(withLock { db.queryList("SELECT * FROM rssSources order by customOrder", emptyList(), RssSource::class.java) })
    }

    override fun flowSearch(key: String): Flow<List<RssSource>> = flow {
        val sql = "SELECT * FROM rssSources where sourceName like '%' || ? || '%' " +
            "or sourceUrl like '%' || ? || '%' or sourceGroup like '%' || ? || '%' " +
            "or sourceComment like '%' || ? || '%' order by customOrder"
        emit(withLock { db.queryList(sql, listOf(key, key, key, key), RssSource::class.java) })
    }

    override fun flowGroupSearch(sourceGroup: String): Flow<List<RssSource>> = flow {
        val sql = "SELECT t2.* FROM rssSources AS t2 where ${rssSourceGroupFilter()} order by t2.customOrder"
        emit(withLock { db.queryList(sql, listOf(sourceGroup, sourceGroup), RssSource::class.java) })
    }

    override fun flowEnabled(): Flow<List<RssSource>> = flow {
        emit(withLock { db.queryList("SELECT * FROM rssSources where enabled = 1 order by customOrder", emptyList(), RssSource::class.java) })
    }

    override fun flowDisabled(): Flow<List<RssSource>> = flow {
        emit(withLock { db.queryList("SELECT * FROM rssSources where enabled = 0 order by customOrder", emptyList(), RssSource::class.java) })
    }

    override fun flowLogin(): Flow<List<RssSource>> = flow {
        emit(withLock {
            db.queryList("select * from rssSources where loginUrl is not null and loginUrl != ''", emptyList(), RssSource::class.java)
        })
    }

    override fun flowNoGroup(): Flow<List<RssSource>> = flow {
        emit(withLock { db.queryList("select * from rssSources where ${noGroupFilter()}", emptyList(), RssSource::class.java) })
    }

    override fun flowEnabled(searchKey: String): Flow<List<RssSource>> = flow {
        val sql = "SELECT * FROM rssSources where enabled = 1 " +
            "and (sourceName like '%' || ? || '%' or sourceGroup like '%' || ? || '%' " +
            "or sourceUrl like '%' || ? || '%' or sourceComment like '%' || ? || '%') order by customOrder"
        emit(withLock { db.queryList(sql, listOf(searchKey, searchKey, searchKey, searchKey), RssSource::class.java) })
    }

    override fun flowEnabledByGroup(sourceGroup: String): Flow<List<RssSource>> = flow {
        val sql = "SELECT t2.* FROM rssSources AS t2 where t2.enabled = 1 and ${rssSourceGroupFilter()} order by t2.customOrder"
        emit(withLock { db.queryList(sql, listOf(sourceGroup, sourceGroup), RssSource::class.java) })
    }

    override fun flowGroupsUnProcessed(): Flow<List<String>> = flow {
        emit(withLock {
            db.queryList("select distinct sourceGroup from rssSources where trim(sourceGroup) <> ''", emptyList(), String::class.java)
        })
    }

    override fun flowEnabledGroupsUnProcessed(): Flow<List<String>> = flow {
        emit(withLock {
            db.queryList("select distinct sourceGroup from rssSources where trim(sourceGroup) <> '' and enabled = 1", emptyList(), String::class.java)
        })
    }

    override val allGroupsUnProcessed: List<String> get() = withLock {
        db.queryList("select distinct sourceGroup from rssSources where trim(sourceGroup) <> ''", emptyList(), String::class.java)
    }

    override val minOrder: Int get() = withLock {
        db.queryValue("select min(customOrder) from rssSources", emptyList(), Int::class.java) ?: 0
    }

    override val maxOrder: Int get() = withLock {
        db.queryValue("select max(customOrder) from rssSources", emptyList(), Int::class.java) ?: 0
    }

    override fun insert(vararg rssSource: RssSource) {
        withLock {
            db.execute(
                "INSERT OR REPLACE INTO rssSources (" +
                    "sourceUrl, sourceName, sourceIcon, sourceGroup, sourceComment, enabled, variableComment, " +
                    "jsLib, enabledCookieJar, concurrentRate, header, loginUrl, loginUi, loginCheckJs, " +
                    "coverDecodeJs, sortUrl, singleUrl, articleStyle, ruleArticles, ruleNextPage, ruleTitle, " +
                    "rulePubDate, ruleDescription, ruleImage, ruleLink, ruleContent, contentWhitelist, " +
                    "contentBlacklist, shouldOverrideUrlLoading, style, enableJs, loadWithBaseUrl, injectJs, " +
                    "preloadJs, startHtml, startStyle, startJs, showWebLog, lastUpdateTime, customOrder, type, " +
                    "preload, cacheFirst, searchUrl" +
                    ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                rssSource.flatMap { s ->
                    listOf(
                        s.sourceUrl, s.sourceName, s.sourceIcon, s.sourceGroup, s.sourceComment, s.enabled,
                        s.variableComment, s.jsLib, s.enabledCookieJar, s.concurrentRate, s.header,
                        s.loginUrl, s.loginUi, s.loginCheckJs, s.coverDecodeJs, s.sortUrl, s.singleUrl,
                        s.articleStyle, s.ruleArticles, s.ruleNextPage, s.ruleTitle, s.rulePubDate,
                        s.ruleDescription, s.ruleImage, s.ruleLink, s.ruleContent, s.contentWhitelist,
                        s.contentBlacklist, s.shouldOverrideUrlLoading, s.style, s.enableJs,
                        s.loadWithBaseUrl, s.injectJs, s.preloadJs, s.startHtml, s.startStyle, s.startJs,
                        s.showWebLog, s.lastUpdateTime, s.customOrder, s.type, s.preload, s.cacheFirst,
                        s.searchUrl
                    )
                }
            )
        }
    }

    override fun update(vararg rssSource: RssSource) {
        withLock {
            db.execute(
                "UPDATE rssSources SET sourceName=?, sourceIcon=?, sourceGroup=?, sourceComment=?, " +
                    "enabled=?, variableComment=?, jsLib=?, enabledCookieJar=?, concurrentRate=?, header=?, " +
                    "loginUrl=?, loginUi=?, loginCheckJs=?, coverDecodeJs=?, sortUrl=?, singleUrl=?, " +
                    "articleStyle=?, ruleArticles=?, ruleNextPage=?, ruleTitle=?, rulePubDate=?, " +
                    "ruleDescription=?, ruleImage=?, ruleLink=?, ruleContent=?, contentWhitelist=?, " +
                    "contentBlacklist=?, shouldOverrideUrlLoading=?, style=?, enableJs=?, loadWithBaseUrl=?, " +
                    "injectJs=?, preloadJs=?, startHtml=?, startStyle=?, startJs=?, showWebLog=?, " +
                    "lastUpdateTime=?, customOrder=?, type=?, preload=?, cacheFirst=?, searchUrl=? WHERE sourceUrl=?",
                rssSource.flatMap { s ->
                    listOf(
                        s.sourceName, s.sourceIcon, s.sourceGroup, s.sourceComment, s.enabled,
                        s.variableComment, s.jsLib, s.enabledCookieJar, s.concurrentRate, s.header,
                        s.loginUrl, s.loginUi, s.loginCheckJs, s.coverDecodeJs, s.sortUrl, s.singleUrl,
                        s.articleStyle, s.ruleArticles, s.ruleNextPage, s.ruleTitle, s.rulePubDate,
                        s.ruleDescription, s.ruleImage, s.ruleLink, s.ruleContent, s.contentWhitelist,
                        s.contentBlacklist, s.shouldOverrideUrlLoading, s.style, s.enableJs,
                        s.loadWithBaseUrl, s.injectJs, s.preloadJs, s.startHtml, s.startStyle, s.startJs,
                        s.showWebLog, s.lastUpdateTime, s.customOrder, s.type, s.preload, s.cacheFirst,
                        s.searchUrl, s.sourceUrl
                    )
                }
            )
        }
    }

    override fun delete(vararg rssSource: RssSource) {
        withLock {
            db.execute("delete from rssSources where sourceUrl = ?", rssSource.map { it.sourceUrl })
        }
    }

    override fun delete(sourceUrl: String) {
        withLock {
            db.execute("delete from rssSources where sourceUrl = ?", listOf(sourceUrl))
        }
    }

    override fun deleteDefault() {
        withLock {
            db.execute("delete from rssSources where sourceGroup like 'legado'", emptyList())
        }
    }

    override val noGroup: List<RssSource> get() = withLock {
        db.queryList("select * from rssSources where sourceGroup is null or sourceGroup = ''", emptyList(), RssSource::class.java)
    }

    override fun getByGroup(group: String): List<RssSource> = withLock {
        db.queryList("select * from rssSources where sourceGroup like '%' || ? || '%'", listOf(group), RssSource::class.java)
    }

    override fun has(key: String): Boolean = withLock {
        db.queryValue("select exists(select 1 from rssSources where sourceUrl = ?)", listOf(key), Int::class.java) == 1
    }

    override fun enable(sourceUrl: String, enable: Boolean) {
        withLock {
            db.execute("update rssSources set enabled = ? where sourceUrl = ?", listOf(enable, sourceUrl))
        }
    }

    // ---- 内部 SQL 片段（对照 Legado RSS_SOURCE_GROUP_FILTER / NO_GROUP_FILTER） ----

    private fun rssSourceGroupFilter(): String =
        "trim(:sourceGroup, $GROUP_TRIM_CHARACTERS) <> ''\n" +
            "and exists (\n" +
            "    with recursive rss_source_groups(group_name, rest) as (\n" +
            "        select '',\n" +
            "            replace(replace(replace(coalesce(t2.sourceGroup, ''), ';', ','), '，', ','), '；', ',') || ','\n" +
            "        union all\n" +
            "        select\n" +
            "            trim(substr(rest, 1, instr(rest, ',') - 1), $GROUP_TRIM_CHARACTERS),\n" +
            "            substr(rest, instr(rest, ',') + 1)\n" +
            "        from rss_source_groups\n" +
            "        where rest <> ''\n" +
            "    )\n" +
            "    select 1\n" +
            "    from rss_source_groups\n" +
            "    where group_name = trim(:sourceGroup, $GROUP_TRIM_CHARACTERS)\n" +
            ")"

    private fun noGroupFilter(): String =
        "trim(coalesce(sourceGroup, ''), $GROUP_TRIM_CHARACTERS) in ('', '未分组')"

    companion object {
        private const val GROUP_TRIM_CHARACTERS =
            "char(9,10,11,12,13,28,29,30,31,32,160,5760,8192,8193,8194,8195,8196," +
                "8197,8198,8199,8200,8201,8202,8232,8233,8239,8287,12288)"
    }
}
