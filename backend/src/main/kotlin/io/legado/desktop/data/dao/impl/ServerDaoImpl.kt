package io.legado.desktop.data.dao.impl

import io.legado.desktop.data.SqlExecutor.execute
import io.legado.desktop.data.SqlExecutor.queryList
import io.legado.desktop.data.SqlExecutor.queryOne
import io.legado.desktop.data.SqliteDatabase
import io.legado.desktop.data.dao.ServerDao
import io.legado.desktop.data.entities.Server
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** ServerDao SQLite 实现（SQL 对照 Legado Room @Query；type 枚举以 name 字符串存取） */
class ServerDaoImpl : ServerDao {

    private val db get() = SqliteDatabase.get()
    private val lock get() = SqliteDatabase.dbLock

    private fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    override fun observeAll(): Flow<List<Server>> = flow {
        emit(withLock { db.queryList("select * from servers order by sortNumber", emptyList(), Server::class.java) })
    }

    override val all: List<Server> get() = withLock {
        db.queryList("select * from servers order by sortNumber", emptyList(), Server::class.java)
    }

    override fun get(id: Long): Server? = withLock {
        db.queryOne("select * from servers where id = ?", listOf(id), Server::class.java)
    }

    override fun insert(vararg server: Server) {
        if (server.isEmpty()) return // 原版 Room @Insert(空列表) 为 no-op，避免空 SQL 全 NULL 触发 NOT NULL
        withLock {
            db.execute(
                "INSERT OR REPLACE INTO servers (id, name, type, config, sortNumber) VALUES (?,?,?,?,?)",
                server.flatMap { s ->
                    listOf(s.id, s.name, s.type.name, s.config, s.sortNumber)
                }
            )
        }
    }

    override fun update(vararg server: Server) {
        withLock {
            db.execute(
                "UPDATE servers SET name=?, type=?, config=?, sortNumber=? WHERE id=?",
                server.flatMap { s ->
                    listOf(s.name, s.type.name, s.config, s.sortNumber, s.id)
                }
            )
        }
    }

    override fun delete(vararg server: Server) {
        withLock {
            db.execute("DELETE FROM servers WHERE id = ?", server.map { it.id })
        }
    }

    override fun delete(id: Long) {
        withLock {
            db.execute("delete from servers where id = ?", listOf(id))
        }
    }

    override fun deleteDefault() {
        withLock {
            db.execute("delete from servers where id < 0", emptyList())
        }
    }
}
