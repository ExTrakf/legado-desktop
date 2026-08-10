package io.legado.desktop.data.dao.impl

import io.legado.desktop.data.SqlExecutor.execute
import io.legado.desktop.data.SqlExecutor.queryList
import io.legado.desktop.data.SqlExecutor.queryOne
import io.legado.desktop.data.SqlExecutor.queryValue
import io.legado.desktop.data.SqliteDatabase
import io.legado.desktop.data.dao.HighlightRuleDao
import io.legado.desktop.data.entities.HighlightRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** HighlightRuleDao SQLite 实现（SQL 对照 Legado Room @Query） */
class HighlightRuleDaoImpl : HighlightRuleDao {

    private val db get() = SqliteDatabase.get()
    private val lock get() = SqliteDatabase.dbLock

    private fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    override val all: List<HighlightRule> get() = withLock {
        db.queryList("SELECT * FROM highlightRules ORDER BY sortOrder ASC", emptyList(), HighlightRule::class.java)
    }

    override fun flowAll(): Flow<List<HighlightRule>> = flow {
        emit(withLock { db.queryList("SELECT * FROM highlightRules ORDER BY sortOrder ASC", emptyList(), HighlightRule::class.java) })
    }

    override fun findById(id: Long): HighlightRule? = withLock {
        db.queryOne("SELECT * FROM highlightRules WHERE id = ?", listOf(id), HighlightRule::class.java)
    }

    override fun findEnabledByBook(name: String, origin: String): List<HighlightRule> = withLock {
        val sql = "SELECT * FROM highlightRules WHERE isEnabled = 1 " +
            "AND (scope IS NULL OR scope = '' " +
            "OR (? != '' AND instr(scope, ?) > 0) " +
            "OR (? != '' AND instr(scope, ?) > 0)) " +
            "ORDER BY sortOrder ASC"
        db.queryList(sql, listOf(name, name, origin, origin), HighlightRule::class.java)
    }

    override val minOrder: Int get() = withLock {
        db.queryValue("SELECT ifnull(min(sortOrder), 0) FROM highlightRules", emptyList(), Int::class.java) ?: 0
    }

    override val maxOrder: Int get() = withLock {
        db.queryValue("SELECT ifnull(max(sortOrder), 0) FROM highlightRules", emptyList(), Int::class.java) ?: 0
    }

    override fun insert(vararg rule: HighlightRule): List<Long> = withLock {
        db.execute(
            "INSERT OR REPLACE INTO highlightRules (id, name, pattern, isRegex, scope, isEnabled, style, " +
                "sortOrder, timeoutMillisecond, applyToTitle) VALUES (?,?,?,?,?,?,?,?,?,?)",
            rule.flatMap { r ->
                listOf(
                    r.id, r.name, r.pattern, r.isRegex, r.scope, r.isEnabled, r.style,
                    r.order, r.timeoutMillisecond, r.applyToTitle
                )
            }
        )
        rule.map { it.id }
    }

    override fun update(vararg rule: HighlightRule) {
        withLock {
            db.execute(
                "UPDATE highlightRules SET name=?, pattern=?, isRegex=?, scope=?, isEnabled=?, style=?, " +
                    "sortOrder=?, timeoutMillisecond=?, applyToTitle=? WHERE id=?",
                rule.flatMap { r ->
                    listOf(
                        r.name, r.pattern, r.isRegex, r.scope, r.isEnabled, r.style,
                        r.order, r.timeoutMillisecond, r.applyToTitle, r.id
                    )
                }
            )
        }
    }

    override fun delete(vararg rule: HighlightRule) {
        withLock {
            db.execute("DELETE FROM highlightRules WHERE id = ?", rule.map { it.id })
        }
    }

    override fun deleteAll() {
        withLock {
            db.execute("DELETE FROM highlightRules", emptyList())
        }
    }
}
