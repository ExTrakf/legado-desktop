package io.legado.desktop.data.dao.impl

import io.legado.desktop.data.SqlExecutor.execute
import io.legado.desktop.data.SqlExecutor.queryOne
import io.legado.desktop.data.SqlExecutor.queryValue
import io.legado.desktop.data.SqliteDatabase
import io.legado.desktop.data.dao.CacheDao
import io.legado.desktop.data.entities.Cache

/** CacheDao SQLite 实现（SQL 对照 Legado Room @Query） */
class CacheDaoImpl : CacheDao {

    private val db get() = SqliteDatabase.get()
    private val lock get() = SqliteDatabase.dbLock

    private fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    override fun get(key: String): Cache? = withLock {
        db.queryOne("select * from caches where `key` = ?", listOf(key), Cache::class.java)
    }

    override fun get(key: String, now: Long): String? = withLock {
        db.queryValue(
            "select value from caches where `key` = ? and (deadline = 0 or deadline > ?)",
            listOf(key, now), String::class.java
        )
    }

    override fun insert(vararg cache: Cache) {
        withLock {
            db.execute(
                "INSERT OR REPLACE INTO caches (`key`, value, deadline) VALUES (?,?,?)",
                cache.flatMap { listOf(it.key, it.value, it.deadline) }
            )
        }
    }

    override fun delete(key: String) {
        withLock {
            db.execute("delete from caches where `key` = ?", listOf(key))
        }
    }

    override fun deleteSourceVariables(key: String) {
        withLock {
            db.execute(
                "delete from caches where `key` like 'v_' || ? || '_%' " +
                    "or `key` = 'userInfo_' || ? " +
                    "or `key` = 'loginHeader_' || ? " +
                    "or `key` = 'sourceVariable_' || ? " +
                    "or `key` = 'infoMap_' || ?",
                listOf(key, key, key, key, key)
            )
        }
    }

    override fun clearDeadline(now: Long) {
        withLock {
            db.execute("delete from caches where deadline > 0 and deadline < ?", listOf(now))
        }
    }
}
