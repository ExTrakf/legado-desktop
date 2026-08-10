package io.legado.desktop.data.dao.impl

import io.legado.desktop.data.SqlExecutor.execute
import io.legado.desktop.data.SqlExecutor.queryList
import io.legado.desktop.data.SqlExecutor.queryOne
import io.legado.desktop.data.SqlExecutor.queryValue
import io.legado.desktop.data.SqliteDatabase
import io.legado.desktop.data.dao.TxtTocRuleDao
import io.legado.desktop.data.entities.TxtTocRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** TxtTocRuleDao SQLite 实现（SQL 对照 Legado Room @Query） */
class TxtTocRuleDaoImpl : TxtTocRuleDao {

    private val db get() = SqliteDatabase.get()
    private val lock get() = SqliteDatabase.dbLock

    private fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    override fun observeAll(): Flow<List<TxtTocRule>> = flow {
        emit(withLock { db.queryList("select * from txtTocRules order by serialNumber", emptyList(), TxtTocRule::class.java) })
    }

    override val all: List<TxtTocRule> get() = withLock {
        db.queryList("select * from txtTocRules order by serialNumber", emptyList(), TxtTocRule::class.java)
    }

    override val enabled: List<TxtTocRule> get() = withLock {
        db.queryList("select * from txtTocRules where enable = 1 order by serialNumber", emptyList(), TxtTocRule::class.java)
    }

    override val disabled: List<TxtTocRule> get() = withLock {
        db.queryList("select * from txtTocRules where enable != 1 order by serialNumber", emptyList(), TxtTocRule::class.java)
    }

    override val count: Int get() = withLock {
        db.queryValue("select count(*) from txtTocRules", emptyList(), Int::class.java) ?: 0
    }

    override fun get(id: Long): TxtTocRule? = withLock {
        db.queryOne("select * from txtTocRules where id = ?", listOf(id), TxtTocRule::class.java)
    }

    override val minOrder: Int get() = withLock {
        db.queryValue("select ifNull(min(serialNumber), 0) from txtTocRules", emptyList(), Int::class.java) ?: 0
    }

    override val maxOrder: Int get() = withLock {
        db.queryValue("select ifNull(max(serialNumber), 0) from txtTocRules", emptyList(), Int::class.java) ?: 0
    }

    override fun insert(vararg rule: TxtTocRule) {
        withLock {
            db.execute(
                "INSERT OR REPLACE INTO txtTocRules (id, name, rule, replacement, example, serialNumber, enable) " +
                    "VALUES (?,?,?,?,?,?,?)",
                rule.flatMap { r ->
                    listOf(r.id, r.name, r.rule, r.replacement, r.example, r.serialNumber, r.enable)
                }
            )
        }
    }

    override fun update(vararg rule: TxtTocRule) {
        withLock {
            db.execute(
                "UPDATE txtTocRules SET name=?, rule=?, replacement=?, example=?, serialNumber=?, enable=? WHERE id=?",
                rule.flatMap { r ->
                    listOf(r.name, r.rule, r.replacement, r.example, r.serialNumber, r.enable, r.id)
                }
            )
        }
    }

    override fun delete(vararg rule: TxtTocRule) {
        withLock {
            db.execute("DELETE FROM txtTocRules WHERE id = ?", rule.map { it.id })
        }
    }

    override fun deleteDefault() {
        withLock {
            db.execute("delete from txtTocRules where id < 0", emptyList())
        }
    }
}
