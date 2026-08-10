package io.legado.desktop.data.dao.impl

import io.legado.desktop.data.SqlExecutor.execute
import io.legado.desktop.data.SqlExecutor.queryList
import io.legado.desktop.data.SqlExecutor.queryOne
import io.legado.desktop.data.SqlExecutor.queryValue
import io.legado.desktop.data.SqliteDatabase
import io.legado.desktop.data.dao.BookSourceDao
import io.legado.desktop.data.entities.BookSource
import io.legado.desktop.data.entities.BookSourcePart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** BookSourceDao SQLite 实现（对照 Legado Room @Query + @DatabaseView book_sources_part） */
class BookSourceDaoImpl : BookSourceDao {

    private val db get() = SqliteDatabase.get()
    private val lock get() = SqliteDatabase.dbLock

    private fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    // ---- Flow 查询（一次性） ----

    override fun flowAll(): Flow<List<BookSourcePart>> = flow {
        emit(withLock { db.queryList("select * from book_sources_part order by customOrder asc", emptyList(), BookSourcePart::class.java) })
    }

    override fun flowSearch(searchKey: String): Flow<List<BookSourcePart>> = flow {
        val sql = "select bp.* from book_sources b join book_sources_part bp on b.bookSourceUrl = bp.bookSourceUrl " +
            "where b.bookSourceName like '%' || ? || '%' or b.bookSourceGroup like '%' || ? || '%' " +
            "or b.bookSourceUrl like '%' || ? || '%' or b.bookSourceComment like '%' || ? || '%' " +
            "order by b.customOrder asc"
        emit(withLock { db.queryList(sql, listOf(searchKey, searchKey, searchKey, searchKey), BookSourcePart::class.java) })
    }

    override fun flowSearchEnabled(searchKey: String): Flow<List<BookSourcePart>> = flow {
        val sql = "select bp.* from book_sources b join book_sources_part bp on b.bookSourceUrl = bp.bookSourceUrl " +
            "where b.enabled = 1 and (b.bookSourceName like '%' || ? || '%' or b.bookSourceGroup like '%' || ? || '%' " +
            "or b.bookSourceUrl like '%' || ? || '%' or b.bookSourceComment like '%' || ? || '%') " +
            "order by b.customOrder asc"
        emit(withLock { db.queryList(sql, listOf(searchKey, searchKey, searchKey, searchKey), BookSourcePart::class.java) })
    }

    override fun flowGroupSearch(sourceGroup: String): Flow<List<BookSourcePart>> = flow {
        emit(withLock {
            db.queryList(
                "select t2.* from book_sources_part as t2 where ${sourceGroupCondition()} ${sourceGroupMembershipFilter()} order by t2.customOrder asc",
                listOf(sourceGroup, sourceGroup), BookSourcePart::class.java
            )
        })
    }

    override fun flowEnabled(): Flow<List<BookSourcePart>> = flow {
        emit(withLock { db.queryList("select * from book_sources_part where enabled = 1 order by customOrder asc", emptyList(), BookSourcePart::class.java) })
    }

    override fun flowDisabled(): Flow<List<BookSourcePart>> = flow {
        emit(withLock { db.queryList("select * from book_sources_part where enabled = 0 order by customOrder asc", emptyList(), BookSourcePart::class.java) })
    }

    override fun flowExplore(): Flow<List<BookSourcePart>> = flow {
        emit(withLock {
            db.queryList("select * from book_sources_part where enabledExplore = 1 and hasExploreUrl = 1 order by customOrder asc", emptyList(), BookSourcePart::class.java)
        })
    }

    override fun flowLogin(): Flow<List<BookSourcePart>> = flow {
        emit(withLock { db.queryList("select * from book_sources_part where hasLoginUrl = 1 order by customOrder asc", emptyList(), BookSourcePart::class.java) })
    }

    override fun flowNoGroup(): Flow<List<BookSourcePart>> = flow {
        emit(withLock {
            db.queryList("select * from book_sources_part where ${noGroupFilter()} order by customOrder asc", emptyList(), BookSourcePart::class.java)
        })
    }

    override fun flowEnabledExplore(): Flow<List<BookSourcePart>> = flow {
        emit(withLock { db.queryList("select * from book_sources_part where enabledExplore = 1 order by customOrder asc", emptyList(), BookSourcePart::class.java) })
    }

    override fun flowDisabledExplore(): Flow<List<BookSourcePart>> = flow {
        emit(withLock { db.queryList("select * from book_sources_part where enabledExplore = 0 order by customOrder asc", emptyList(), BookSourcePart::class.java) })
    }

    override fun flowExplore(key: String): Flow<List<BookSourcePart>> = flow {
        val sql = "select * from book_sources_part where enabledExplore = 1 and hasExploreUrl = 1 " +
            "and (bookSourceGroup like '%' || ? || '%' or bookSourceName like '%' || ? || '%') order by customOrder asc"
        emit(withLock { db.queryList(sql, listOf(key, key), BookSourcePart::class.java) })
    }

    override fun flowGroupExplore(sourceGroup: String): Flow<List<BookSourcePart>> = flow {
        emit(withLock {
            val sql = "select t2.* from book_sources_part as t2 where t2.enabledExplore = 1 and t2.hasExploreUrl = 1 " +
                "and ${sourceGroupCondition()} ${sourceGroupMembershipFilter()} order by t2.customOrder asc"
            db.queryList(sql, listOf(sourceGroup, sourceGroup), BookSourcePart::class.java)
        })
    }

    override fun flowGroupsUnProcessed(): Flow<List<String>> = flow {
        emit(withLock { db.queryList("select distinct bookSourceGroup from book_sources where trim(bookSourceGroup) <> ''", emptyList(), String::class.java) })
    }

    override fun flowEnabledGroupsUnProcessed(): Flow<List<String>> = flow {
        emit(withLock { db.queryList("select distinct bookSourceGroup from book_sources where enabled = 1 and trim(bookSourceGroup) <> ''", emptyList(), String::class.java) })
    }

    override fun flowExploreGroupsUnProcessed(): Flow<List<String>> = flow {
        emit(withLock {
            val sql = "select distinct bookSourceGroup from book_sources where enabledExplore = 1 " +
                "and trim(exploreUrl) <> '' and trim(bookSourceGroup) <> '' order by customOrder"
            db.queryList(sql, emptyList(), String::class.java)
        })
    }

    // ---- 同步查询 ----

    override fun search(searchKey: String): List<BookSource> = withLock {
        val sql = "select * from book_sources where bookSourceName like '%' || ? || '%' " +
            "or bookSourceGroup like '%' || ? || '%' or bookSourceUrl like '%' || ? || '%' " +
            "or bookSourceComment like '%' || ? || '%' order by customOrder asc"
        db.queryList(sql, listOf(searchKey, searchKey, searchKey, searchKey), BookSource::class.java)
    }

    override fun groupSearch(sourceGroup: String): List<BookSource> = withLock {
        val sql = "select t2.* from book_sources as t2 where ${sourceGroupCondition()} ${sourceGroupMembershipFilter()} order by t2.customOrder asc"
        db.queryList(sql, listOf(sourceGroup, sourceGroup), BookSource::class.java)
    }

    override fun getByGroup(group: String): List<BookSource> = withLock {
        db.queryList("select * from book_sources where bookSourceGroup like '%' || ? || '%' order by customOrder asc", listOf(group), BookSource::class.java)
    }

    override fun getEnabledByGroup(group: String): List<BookSource> = withLock {
        val sql = "select * from book_sources where enabled = 1 " +
            "and (bookSourceGroup = ? or bookSourceGroup like ? || ',%' " +
            "or bookSourceGroup like '%,' || ? or bookSourceGroup like '%,' || ? || ',%') " +
            "order by customOrder asc"
        db.queryList(sql, listOf(group, group, group, group), BookSource::class.java)
    }

    override fun getEnabledPartByGroup(sourceGroup: String): List<BookSourcePart> = withLock {
        val sql = "select t2.* from book_sources_part as t2 where t2.enabled = 1 ${sourceGroupMembershipFilter()} order by t2.customOrder asc"
        db.queryList(sql, listOf(sourceGroup), BookSourcePart::class.java)
    }

    override fun getEnabledByType(type: Int): List<BookSource> = withLock {
        db.queryList("select * from book_sources where bookUrlPattern != 'NONE' and bookSourceType = ? order by customOrder asc", listOf(type), BookSource::class.java)
    }

    override fun getBookSourceAddBook(baseUrl: String): BookSource? = withLock {
        db.queryOne("select * from book_sources where enabled = 1 and bookSourceUrl = ?", listOf(baseUrl), BookSource::class.java)
    }

    override val hasBookUrlPattern: List<BookSourcePart> get() = withLock {
        val sql = "select bp.* from book_sources b join book_sources_part bp on b.bookSourceUrl = bp.bookSourceUrl " +
            "where b.enabled = 1 and trim(b.bookUrlPattern) <> '' and trim(b.bookUrlPattern) <> 'NONE' order by b.customOrder"
        db.queryList(sql, emptyList(), BookSourcePart::class.java)
    }

    override val noGroup: List<BookSource> get() = withLock {
        db.queryList("select * from book_sources where bookSourceGroup is null or bookSourceGroup = ''", emptyList(), BookSource::class.java)
    }

    override val all: List<BookSource> get() = withLock {
        db.queryList("select * from book_sources order by customOrder asc", emptyList(), BookSource::class.java)
    }

    override val allPart: List<BookSourcePart> get() = withLock {
        db.queryList("select * from book_sources_part order by customOrder asc", emptyList(), BookSourcePart::class.java)
    }

    override val allEnabled: List<BookSource> get() = withLock {
        db.queryList("select * from book_sources where enabled = 1 order by customOrder", emptyList(), BookSource::class.java)
    }

    override val allEnabledPart: List<BookSourcePart> get() = withLock {
        db.queryList("select * from book_sources_part where enabled = 1 order by customOrder asc", emptyList(), BookSourcePart::class.java)
    }

    override val allDisabled: List<BookSource> get() = withLock {
        db.queryList("select * from book_sources where enabled = 0 order by customOrder", emptyList(), BookSource::class.java)
    }

    override val allNoGroup: List<BookSource> get() = withLock {
        db.queryList("select * from book_sources where ${noGroupFilter()}", emptyList(), BookSource::class.java)
    }

    override val allEnabledExplore: List<BookSource> get() = withLock {
        db.queryList("select * from book_sources where enabledExplore = 1 order by customOrder", emptyList(), BookSource::class.java)
    }

    override val allDisabledExplore: List<BookSource> get() = withLock {
        db.queryList("select * from book_sources where enabledExplore = 0 order by customOrder", emptyList(), BookSource::class.java)
    }

    override val allLogin: List<BookSource> get() = withLock {
        db.queryList("select * from book_sources where loginUrl is not null and loginUrl != ''", emptyList(), BookSource::class.java)
    }

    override val allTextEnabledPart: List<BookSourcePart> get() = withLock {
        val sql = "select bp.* from book_sources b join book_sources_part bp on b.bookSourceUrl = bp.bookSourceUrl " +
            "where b.enabled = 1 and b.bookSourceType = 0 order by b.customOrder"
        db.queryList(sql, emptyList(), BookSourcePart::class.java)
    }

    override val allGroupsUnProcessed: List<String> get() = withLock {
        db.queryList("select distinct bookSourceGroup from book_sources where trim(bookSourceGroup) <> ''", emptyList(), String::class.java)
    }

    override val allEnabledGroupsUnProcessed: List<String> get() = withLock {
        db.queryList("select distinct bookSourceGroup from book_sources where enabled = 1 and trim(bookSourceGroup) <> ''", emptyList(), String::class.java)
    }

    override fun getBookSource(key: String): BookSource? = withLock {
        db.queryOne("select * from book_sources where bookSourceUrl = ?", listOf(key), BookSource::class.java)
    }

    override fun getBookSources(keys: List<String>): List<BookSource> = withLock {
        db.queryList("select * from book_sources where bookSourceUrl in (:keys)", listOf(keys), BookSource::class.java)
    }

    override fun getBookSourcePart(key: String): BookSourcePart? = withLock {
        db.queryOne("select * from book_sources_part where bookSourceUrl = ?", listOf(key), BookSourcePart::class.java)
    }

    override fun allCount(): Int = withLock {
        db.queryValue("select count(*) from book_sources", emptyList(), Int::class.java) ?: 0
    }

    override fun has(key: String): Boolean = withLock {
        db.queryValue("SELECT EXISTS(select 1 from book_sources where bookSourceUrl = ?)", listOf(key), Int::class.java) == 1
    }

    override fun getMainJs(key: String): String? = withLock {
        db.queryValue("select mainJs from book_sources where bookSourceUrl = ?", listOf(key), String::class.java)
    }

    override fun insert(vararg bookSource: BookSource) {
        withLock {
            db.execute(
                "INSERT OR REPLACE INTO book_sources (" +
                    "bookSourceUrl, bookSourceName, bookSourceGroup, bookSourceType, bookUrlPattern, customOrder, " +
                    "enabled, enabledExplore, jsLib, enabledCookieJar, concurrentRate, header, loginUrl, loginUi, " +
                    "loginCheckJs, coverDecodeJs, bookSourceComment, variableComment, lastUpdateTime, respondTime, " +
                    "weight, exploreUrl, exploreScreen, ruleExplore, searchUrl, ruleSearch, ruleBookInfo, ruleToc, " +
                    "ruleContent, ruleReview, mainJs, eventListener, customButton" +
                    ") VALUES (" +
                    "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                bookSource.flatMap { s ->
                    listOf(
                        s.bookSourceUrl, s.bookSourceName, s.bookSourceGroup, s.bookSourceType, s.bookUrlPattern,
                        s.customOrder, s.enabled, s.enabledExplore, s.jsLib, s.enabledCookieJar, s.concurrentRate,
                        s.header, s.loginUrl, s.loginUi, s.loginCheckJs, s.coverDecodeJs, s.bookSourceComment,
                        s.variableComment, s.lastUpdateTime, s.respondTime, s.weight, s.exploreUrl, s.exploreScreen,
                        s.ruleExplore, s.searchUrl, s.ruleSearch, s.ruleBookInfo, s.ruleToc, s.ruleContent,
                        s.ruleReview, s.mainJs, s.eventListener, s.customButton
                    )
                }
            )
        }
    }

    override fun update(vararg bookSource: BookSource) {
        withLock {
            db.execute(
                "UPDATE book_sources SET bookSourceName=?, bookSourceGroup=?, bookSourceType=?, bookUrlPattern=?, " +
                    "customOrder=?, enabled=?, enabledExplore=?, jsLib=?, enabledCookieJar=?, concurrentRate=?, " +
                    "header=?, loginUrl=?, loginUi=?, loginCheckJs=?, coverDecodeJs=?, bookSourceComment=?, " +
                    "variableComment=?, lastUpdateTime=?, respondTime=?, weight=?, exploreUrl=?, exploreScreen=?, " +
                    "ruleExplore=?, searchUrl=?, ruleSearch=?, ruleBookInfo=?, ruleToc=?, ruleContent=?, " +
                    "ruleReview=?, mainJs=?, eventListener=?, customButton=? WHERE bookSourceUrl=?",
                bookSource.flatMap { s ->
                    listOf(
                        s.bookSourceName, s.bookSourceGroup, s.bookSourceType, s.bookUrlPattern, s.customOrder,
                        s.enabled, s.enabledExplore, s.jsLib, s.enabledCookieJar, s.concurrentRate, s.header,
                        s.loginUrl, s.loginUi, s.loginCheckJs, s.coverDecodeJs, s.bookSourceComment,
                        s.variableComment, s.lastUpdateTime, s.respondTime, s.weight, s.exploreUrl, s.exploreScreen,
                        s.ruleExplore, s.searchUrl, s.ruleSearch, s.ruleBookInfo, s.ruleToc, s.ruleContent,
                        s.ruleReview, s.mainJs, s.eventListener, s.customButton, s.bookSourceUrl
                    )
                }
            )
        }
    }

    override fun updateCheckResult(
        bookSourceUrl: String,
        bookSourceGroup: String?,
        bookSourceComment: String?,
        respondTime: Long,
        expectedLastUpdateTime: Long,
        expectedBookSourceGroup: String?,
        expectedBookSourceComment: String?,
        expectedRespondTime: Long,
    ): Int = withLock {
        db.execute(
            "update book_sources set bookSourceGroup = ?, bookSourceComment = ?, respondTime = ? " +
                "where bookSourceUrl = ? and lastUpdateTime = ? " +
                "and ((bookSourceGroup is null and ? is null) or bookSourceGroup = ?) " +
                "and ((bookSourceComment is null and ? is null) or bookSourceComment = ?) " +
                "and respondTime = ?",
            listOf(
                bookSourceGroup, bookSourceComment, respondTime, bookSourceUrl, expectedLastUpdateTime,
                expectedBookSourceGroup, expectedBookSourceGroup, expectedBookSourceComment,
                expectedBookSourceComment, expectedRespondTime
            )
        )
    }

    override fun delete(vararg bookSource: BookSource) {
        withLock {
            db.execute("delete from book_sources where bookSourceUrl = ?", bookSource.map { it.bookSourceUrl })
        }
    }

    override fun delete(key: String) {
        withLock {
            db.execute("delete from book_sources where bookSourceUrl = ?", listOf(key))
        }
    }

    override val minOrder: Int get() = withLock {
        db.queryValue("select min(customOrder) from book_sources", emptyList(), Int::class.java) ?: 0
    }

    override val maxOrder: Int get() = withLock {
        db.queryValue("select max(customOrder) from book_sources", emptyList(), Int::class.java) ?: 0
    }

    override val hasDuplicateOrder: Boolean get() = withLock {
        db.queryValue("select exists (select 1 from book_sources group by customOrder having count(customOrder) > 1)", emptyList(), Int::class.java) == 1
    }

    override fun enable(bookSourceUrl: String, enable: Boolean) {
        withLock {
            db.execute("update book_sources set enabled = ? where bookSourceUrl = ?", listOf(enable, bookSourceUrl))
        }
    }

    override fun enableExplore(bookSourceUrl: String, enable: Boolean) {
        withLock {
            db.execute("update book_sources set enabledExplore = ? where bookSourceUrl = ?", listOf(enable, bookSourceUrl))
        }
    }

    override fun upOrder(bookSourceUrl: String, customOrder: Int) {
        withLock {
            db.execute("update book_sources set customOrder = ? where bookSourceUrl = ?", listOf(customOrder, bookSourceUrl))
        }
    }

    override fun upGroup(bookSourceUrl: String, bookSourceGroup: String) {
        withLock {
            db.execute("update book_sources set bookSourceGroup = ? where bookSourceUrl = ?", listOf(bookSourceGroup, bookSourceUrl))
        }
    }

    // ---- 内部 SQL 片段 ----

    private fun sourceGroupCondition(): String =
        "trim(:sourceGroup, $GROUP_TRIM_CHARACTERS) <> ''"

    private fun sourceGroupMembershipFilter(): String =
        "and (trim(:sourceGroup, $GROUP_TRIM_CHARACTERS) = '' " +
            "or exists (with recursive source_groups(group_name, rest) as (" +
            "select '', replace(replace(replace(coalesce(t2.bookSourceGroup, ''), ';', ','), '，', ','), '；', ',') || ',' " +
            "union all select trim(substr(rest, 1, instr(rest, ',') - 1), $GROUP_TRIM_CHARACTERS), substr(rest, instr(rest, ',') + 1) " +
            "from source_groups where rest <> '') " +
            "select 1 from source_groups where group_name = trim(:sourceGroup, $GROUP_TRIM_CHARACTERS)))"

    private fun noGroupFilter(): String =
        "trim(coalesce(bookSourceGroup, ''), $GROUP_TRIM_CHARACTERS) in ('', '未分组')"

    companion object {
        private const val GROUP_TRIM_CHARACTERS =
            "char(9,10,11,12,13,28,29,30,31,32,160,5760,8192,8193,8194,8195,8196," +
                "8197,8198,8199,8200,8201,8202,8232,8233,8239,8287,12288)"
    }
}
