package io.legado.desktop.data.dao.impl

import io.legado.desktop.data.SqlExecutor.execute
import io.legado.desktop.data.SqlExecutor.queryList
import io.legado.desktop.data.SqlExecutor.queryOne
import io.legado.desktop.data.SqlExecutor.queryValue
import io.legado.desktop.data.SqliteDatabase
import io.legado.desktop.data.dao.AutoTaskRuleDao
import io.legado.desktop.data.entities.AutoTaskRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** AutoTaskRuleDao SQLite 实现（SQL 对照 Legado Room @Query；@Upsert → INSERT OR REPLACE） */
class AutoTaskRuleDaoImpl : AutoTaskRuleDao {

    private val db get() = SqliteDatabase.get()
    private val lock get() = SqliteDatabase.dbLock

    private fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    override fun all(): List<AutoTaskRule> = withLock {
        db.queryList(
            "SELECT * FROM auto_task_rules ORDER BY customOrder, name COLLATE NOCASE, id",
            emptyList(), AutoTaskRule::class.java
        )
    }

    override fun flowAll(): Flow<List<AutoTaskRule>> = flow {
        emit(withLock {
            db.queryList(
                "SELECT * FROM auto_task_rules ORDER BY customOrder, name COLLATE NOCASE, id",
                emptyList(), AutoTaskRule::class.java
            )
        })
    }

    override fun getById(id: String): AutoTaskRule? = withLock {
        db.queryOne("SELECT * FROM auto_task_rules WHERE id = ?", listOf(id), AutoTaskRule::class.java)
    }

    override fun maxOrder(): Int = withLock {
        db.queryValue("SELECT COALESCE(MAX(customOrder), -1) FROM auto_task_rules", emptyList(), Int::class.java) ?: -1
    }

    override fun upsert(vararg rules: AutoTaskRule) {
        withLock {
            db.execute(
                "INSERT OR REPLACE INTO auto_task_rules (id, name, enable, cron, loginUrl, loginUi, " +
                    "loginCheckJs, comment, script, header, jsLib, concurrentRate, enabledCookieJar, " +
                    "customOrder, lastRunAt, lastResult, lastError, lastLog) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                rules.flatMap { r ->
                    listOf(
                        r.id, r.name, r.enable, r.cron, r.loginUrl, r.loginUi, r.loginCheckJs,
                        r.comment, r.script, r.header, r.jsLib, r.concurrentRate, r.enabledCookieJar,
                        r.customOrder, r.lastRunAt, r.lastResult, r.lastError, r.lastLog
                    )
                }
            )
        }
    }

    override fun update(vararg rules: AutoTaskRule) {
        withLock {
            db.execute(
                "UPDATE auto_task_rules SET name=?, enable=?, cron=?, loginUrl=?, loginUi=?, " +
                    "loginCheckJs=?, comment=?, script=?, header=?, jsLib=?, concurrentRate=?, " +
                    "enabledCookieJar=?, customOrder=?, lastRunAt=?, lastResult=?, lastError=?, lastLog=? WHERE id=?",
                rules.flatMap { r ->
                    listOf(
                        r.name, r.enable, r.cron, r.loginUrl, r.loginUi, r.loginCheckJs, r.comment,
                        r.script, r.header, r.jsLib, r.concurrentRate, r.enabledCookieJar, r.customOrder,
                        r.lastRunAt, r.lastResult, r.lastError, r.lastLog, r.id
                    )
                }
            )
        }
    }

    override fun deleteByIds(ids: Collection<String>) {
        withLock {
            db.execute("DELETE FROM auto_task_rules WHERE id IN (:ids)", listOf(ids))
        }
    }

    override fun updateCron(ids: Collection<String>, cron: String): Int = withLock {
        db.execute("UPDATE auto_task_rules SET cron = ? WHERE id IN (:ids)", listOf(cron, ids))
    }

    override fun updateEnabled(ids: Collection<String>, enabled: Boolean): Int = withLock {
        db.execute("UPDATE auto_task_rules SET enable = ? WHERE id IN (:ids)", listOf(enabled, ids))
    }

    override fun clearRunLog(id: String): Int = withLock {
        db.execute(
            "UPDATE auto_task_rules SET lastResult = NULL, lastError = NULL, lastLog = NULL WHERE id = ?",
            listOf(id)
        )
    }

    override fun updateRunState(id: String, lastRunAt: Long, lastResult: String?, lastError: String?, lastLog: String?) {
        withLock {
            db.execute(
                "UPDATE auto_task_rules SET lastRunAt = ?, lastResult = ?, lastError = ?, lastLog = ? WHERE id = ?",
                listOf(lastRunAt, lastResult, lastError, lastLog, id)
            )
        }
    }
}
