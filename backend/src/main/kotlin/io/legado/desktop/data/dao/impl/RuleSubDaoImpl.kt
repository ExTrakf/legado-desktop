package io.legado.desktop.data.dao.impl

import io.legado.desktop.data.SqlExecutor.execute
import io.legado.desktop.data.SqlExecutor.queryList
import io.legado.desktop.data.SqlExecutor.queryOne
import io.legado.desktop.data.SqlExecutor.queryValue
import io.legado.desktop.data.SqliteDatabase
import io.legado.desktop.data.dao.RuleSubDao
import io.legado.desktop.data.entities.RuleSub
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** RuleSubDao SQLite 实现（SQL 对照 Legado Room @Query） */
class RuleSubDaoImpl : RuleSubDao {

    private val db get() = SqliteDatabase.get()
    private val lock get() = SqliteDatabase.dbLock

    private fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    override val all: List<RuleSub> get() = withLock {
        db.queryList("select * from ruleSubs order by customOrder", emptyList(), RuleSub::class.java)
    }

    override fun flowAll(): Flow<List<RuleSub>> = flow {
        emit(withLock { db.queryList("select * from ruleSubs order by customOrder", emptyList(), RuleSub::class.java) })
    }

    override val maxOrder: Int get() = withLock {
        // 与原版 Room @Query 完全一致（select customOrder ... limit 0,1）
        db.queryValue("select customOrder from ruleSubs order by customOrder limit 0,1", emptyList(), Int::class.java) ?: 0
    }

    override fun findByUrl(url: String): RuleSub? = withLock {
        db.queryOne("select * from ruleSubs where url = ?", listOf(url), RuleSub::class.java)
    }

    override fun insert(vararg ruleSub: RuleSub) {
        withLock {
            db.execute(
                "INSERT OR REPLACE INTO ruleSubs (id, name, url, type, customOrder, autoUpdate, `update`, " +
                    "updateInterval, silentUpdate, js, showRule, sourceUrl) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                ruleSub.flatMap { r ->
                    listOf(
                        r.id, r.name, r.url, r.type, r.customOrder, r.autoUpdate, r.update,
                        r.updateInterval, r.silentUpdate, r.js, r.showRule, r.sourceUrl
                    )
                }
            )
        }
    }

    override fun delete(vararg ruleSub: RuleSub) {
        withLock {
            db.execute("DELETE FROM ruleSubs WHERE id = ?", ruleSub.map { it.id })
        }
    }

    override fun update(vararg ruleSub: RuleSub) {
        withLock {
            db.execute(
                "UPDATE ruleSubs SET name=?, url=?, type=?, customOrder=?, autoUpdate=?, `update`=?, " +
                    "updateInterval=?, silentUpdate=?, js=?, showRule=?, sourceUrl=? WHERE id=?",
                ruleSub.flatMap { r ->
                    listOf(
                        r.name, r.url, r.type, r.customOrder, r.autoUpdate, r.update,
                        r.updateInterval, r.silentUpdate, r.js, r.showRule, r.sourceUrl, r.id
                    )
                }
            )
        }
    }
}
