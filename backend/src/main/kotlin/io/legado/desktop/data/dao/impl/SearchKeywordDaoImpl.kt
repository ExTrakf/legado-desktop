package io.legado.desktop.data.dao.impl

import io.legado.desktop.data.SqlExecutor.execute
import io.legado.desktop.data.SqlExecutor.queryList
import io.legado.desktop.data.SqlExecutor.queryOne
import io.legado.desktop.data.SqliteDatabase
import io.legado.desktop.data.dao.SearchKeywordDao
import io.legado.desktop.data.entities.SearchKeyword
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** SearchKeywordDao SQLite 实现（SQL 对照 Legado Room @Query） */
class SearchKeywordDaoImpl : SearchKeywordDao {

    private val db get() = SqliteDatabase.get()
    private val lock get() = SqliteDatabase.dbLock

    private fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    override val all: List<SearchKeyword> get() = withLock {
        db.queryList("SELECT * FROM search_keywords", emptyList(), SearchKeyword::class.java)
    }

    override fun flowByUsage(): Flow<List<SearchKeyword>> = flow {
        emit(withLock { db.queryList("SELECT * FROM search_keywords ORDER BY usage DESC", emptyList(), SearchKeyword::class.java) })
    }

    override fun flowByTime(): Flow<List<SearchKeyword>> = flow {
        emit(withLock { db.queryList("SELECT * FROM search_keywords ORDER BY lastUseTime DESC", emptyList(), SearchKeyword::class.java) })
    }

    override fun flowSearch(key: String): Flow<List<SearchKeyword>> = flow {
        val sql = "SELECT * FROM search_keywords where word like '%'||?||'%' ORDER BY usage DESC"
        emit(withLock { db.queryList(sql, listOf(key), SearchKeyword::class.java) })
    }

    override fun get(key: String): SearchKeyword? = withLock {
        db.queryOne("select * from search_keywords where word = ?", listOf(key), SearchKeyword::class.java)
    }

    override fun insert(vararg keywords: SearchKeyword) {
        withLock {
            db.execute(
                "INSERT OR REPLACE INTO search_keywords (word, usage, lastUseTime) VALUES (?,?,?)",
                keywords.flatMap { listOf(it.word, it.usage, it.lastUseTime) }
            )
        }
    }

    override fun update(vararg keywords: SearchKeyword) {
        withLock {
            db.execute(
                "UPDATE search_keywords SET usage=?, lastUseTime=? WHERE word=?",
                keywords.flatMap { listOf(it.usage, it.lastUseTime, it.word) }
            )
        }
    }

    override fun delete(vararg keywords: SearchKeyword) {
        withLock {
            db.execute("DELETE FROM search_keywords WHERE word = ?", keywords.map { it.word })
        }
    }

    override fun deleteAll() {
        withLock {
            db.execute("DELETE FROM search_keywords", emptyList())
        }
    }
}
