package io.legado.desktop.data.dao.impl

import io.legado.desktop.data.SqlExecutor.execute
import io.legado.desktop.data.SqlExecutor.queryList
import io.legado.desktop.data.SqlExecutor.queryOne
import io.legado.desktop.data.SqliteDatabase
import io.legado.desktop.data.dao.DictRuleDao
import io.legado.desktop.data.entities.DictRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** DictRuleDao SQLite 实现（SQL 对照 Legado Room @Query） */
class DictRuleDaoImpl : DictRuleDao {

    private val db get() = SqliteDatabase.get()
    private val lock get() = SqliteDatabase.dbLock

    private fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    override val all: List<DictRule> get() = withLock {
        db.queryList("select * from dictRules order by sortNumber", emptyList(), DictRule::class.java)
    }

    override val enabled: List<DictRule> get() = withLock {
        db.queryList("select * from dictRules where enabled = 1 order by sortNumber", emptyList(), DictRule::class.java)
    }

    override fun flowAll(): Flow<List<DictRule>> = flow {
        emit(withLock { db.queryList("select * from dictRules order by sortNumber", emptyList(), DictRule::class.java) })
    }

    override fun getByName(name: String): DictRule? = withLock {
        db.queryOne("select * from dictRules where name = ?", listOf(name), DictRule::class.java)
    }

    override fun insert(vararg dictRule: DictRule) {
        withLock {
            db.execute(
                "INSERT OR REPLACE INTO dictRules (name, urlRule, showRule, enabled, sortNumber) VALUES (?,?,?,?,?)",
                dictRule.flatMap { r ->
                    listOf(r.name, r.urlRule, r.showRule, r.enabled, r.sortNumber)
                }
            )
        }
    }

    override fun update(vararg dictRule: DictRule) {
        withLock {
            db.execute(
                "UPDATE dictRules SET urlRule=?, showRule=?, enabled=?, sortNumber=? WHERE name=?",
                dictRule.flatMap { r ->
                    listOf(r.urlRule, r.showRule, r.enabled, r.sortNumber, r.name)
                }
            )
        }
    }

    override fun delete(vararg dictRule: DictRule) {
        withLock {
            db.execute("DELETE FROM dictRules WHERE name = ?", dictRule.map { it.name })
        }
    }
}
