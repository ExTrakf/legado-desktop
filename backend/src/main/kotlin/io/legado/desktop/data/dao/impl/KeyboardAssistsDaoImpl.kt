package io.legado.desktop.data.dao.impl

import io.legado.desktop.data.SqlExecutor.execute
import io.legado.desktop.data.SqlExecutor.queryList
import io.legado.desktop.data.SqlExecutor.queryValue
import io.legado.desktop.data.SqliteDatabase
import io.legado.desktop.data.dao.KeyboardAssistsDao
import io.legado.desktop.data.entities.KeyboardAssist
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** KeyboardAssistsDao SQLite 实现（SQL 对照 Legado Room @Query；deleteAll 为 suspend 桌面版） */
class KeyboardAssistsDaoImpl : KeyboardAssistsDao {

    private val db get() = SqliteDatabase.get()
    private val lock get() = SqliteDatabase.dbLock

    private fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    override val all: List<KeyboardAssist> get() = withLock {
        db.queryList("select * from keyboardAssists order by serialNo", emptyList(), KeyboardAssist::class.java)
    }

    override fun getByType(type: Int): List<KeyboardAssist> = withLock {
        db.queryList("select * from keyboardAssists where type = ? order by serialNo", listOf(type), KeyboardAssist::class.java)
    }

    override val flowAll: Flow<List<KeyboardAssist>> get() = flow {
        emit(withLock { db.queryList("select * from keyboardAssists order by serialNo", emptyList(), KeyboardAssist::class.java) })
    }

    override fun flowByType(type: Int): Flow<List<KeyboardAssist>> = flow {
        emit(withLock {
            db.queryList("select * from keyboardAssists where type = ? order by serialNo", listOf(type), KeyboardAssist::class.java)
        })
    }

    override val maxSerialNo: Int get() = withLock {
        db.queryValue("select max(serialNo) from keyboardAssists order by serialNo", emptyList(), Int::class.java) ?: 0
    }

    override fun insert(vararg keyboardAssist: KeyboardAssist) {
        withLock {
            db.execute(
                "INSERT OR REPLACE INTO keyboardAssists (type, `key`, value, serialNo) VALUES (?,?,?,?)",
                keyboardAssist.flatMap { k ->
                    listOf(k.type, k.key, k.value, k.serialNo)
                }
            )
        }
    }

    override fun update(vararg keyboardAssist: KeyboardAssist) {
        withLock {
            db.execute(
                "UPDATE keyboardAssists SET value=?, serialNo=? WHERE type=? AND `key`=?",
                keyboardAssist.flatMap { k ->
                    listOf(k.value, k.serialNo, k.type, k.key)
                }
            )
        }
    }

    override fun delete(vararg keyboardAssist: KeyboardAssist) {
        withLock {
            db.execute(
                "DELETE FROM keyboardAssists WHERE type = ? AND `key` = ?",
                keyboardAssist.flatMap { listOf(it.type, it.key) }
            )
        }
    }

    override suspend fun deleteAll() {
        withLock {
            db.execute("delete from keyboardAssists", emptyList())
        }
    }
}
