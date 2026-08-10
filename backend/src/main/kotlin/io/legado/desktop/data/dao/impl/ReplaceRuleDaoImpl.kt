package io.legado.desktop.data.dao.impl

import io.legado.desktop.data.SqlExecutor.execute
import io.legado.desktop.data.SqlExecutor.queryList
import io.legado.desktop.data.SqlExecutor.queryOne
import io.legado.desktop.data.SqlExecutor.queryValue
import io.legado.desktop.data.SqliteDatabase
import io.legado.desktop.data.dao.ReplaceRuleDao
import io.legado.desktop.data.entities.ReplaceRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** ReplaceRuleDao SQLite 实现（SQL 对照 Legado Room @Query） */
class ReplaceRuleDaoImpl : ReplaceRuleDao {

    private val db get() = SqliteDatabase.get()
    private val lock get() = SqliteDatabase.dbLock

    private fun <T> withLock(block: () -> T): T = synchronized(lock, block)

    override fun flowAll(): Flow<List<ReplaceRule>> = flow {
        emit(withLock { db.queryList("SELECT * FROM replace_rules ORDER BY sortOrder ASC", emptyList(), ReplaceRule::class.java) })
    }

    override fun flowSearch(key: String): Flow<List<ReplaceRule>> = flow {
        val sql = "SELECT * FROM replace_rules where `group` like ? or name like ? ORDER BY sortOrder ASC"
        emit(withLock { db.queryList(sql, listOf(key, key), ReplaceRule::class.java) })
    }

    override fun flowEnabled(): Flow<List<ReplaceRule>> = flow {
        emit(withLock { db.queryList("SELECT * FROM replace_rules WHERE isEnabled = 1 ORDER BY sortOrder ASC", emptyList(), ReplaceRule::class.java) })
    }

    override fun flowDisabled(): Flow<List<ReplaceRule>> = flow {
        emit(withLock { db.queryList("SELECT * FROM replace_rules WHERE isEnabled = 0 ORDER BY sortOrder ASC", emptyList(), ReplaceRule::class.java) })
    }

    override fun flowGroupSearch(groupName: String): Flow<List<ReplaceRule>> = flow {
        val sql = "SELECT t2.* FROM replace_rules AS t2 WHERE ${replaceRuleGroupFilter()} ORDER BY t2.sortOrder ASC"
        emit(withLock { db.queryList(sql, listOf(groupName, groupName), ReplaceRule::class.java) })
    }

    override fun flowGroupsUnProcessed(): Flow<List<String>> = flow {
        emit(withLock {
            db.queryList("select `group` from replace_rules where `group` is not null and `group` <> ''", emptyList(), String::class.java)
        })
    }

    override fun flowNoGroup(): Flow<List<ReplaceRule>> = flow {
        emit(withLock { db.queryList("select * from replace_rules where ${noGroupFilter()}", emptyList(), ReplaceRule::class.java) })
    }

    override val minOrder: Int get() = withLock {
        db.queryValue("SELECT MIN(sortOrder) FROM replace_rules", emptyList(), Int::class.java) ?: 0
    }

    override val maxOrder: Int get() = withLock {
        db.queryValue("SELECT MAX(sortOrder) FROM replace_rules", emptyList(), Int::class.java) ?: 0
    }

    override val all: List<ReplaceRule> get() = withLock {
        db.queryList("SELECT * FROM replace_rules ORDER BY sortOrder ASC", emptyList(), ReplaceRule::class.java)
    }

    override val allGroupsUnProcessed: List<String> get() = withLock {
        db.queryList("select distinct `group` from replace_rules where trim(`group`) <> ''", emptyList(), String::class.java)
    }

    override val allEnabled: List<ReplaceRule> get() = withLock {
        db.queryList("SELECT * FROM replace_rules WHERE isEnabled = 1 ORDER BY sortOrder ASC", emptyList(), ReplaceRule::class.java)
    }

    override fun findById(id: Long): ReplaceRule? = withLock {
        db.queryOne("SELECT * FROM replace_rules WHERE id = ?", listOf(id), ReplaceRule::class.java)
    }

    override fun findByIds(vararg ids: Long): List<ReplaceRule> = withLock {
        db.queryList("SELECT * FROM replace_rules WHERE id in (:ids)", listOf(ids.toList()), ReplaceRule::class.java)
    }

    override fun findEnabledByContentScope(name: String, origin: String): List<ReplaceRule> = withLock {
        val sql = "SELECT * FROM replace_rules WHERE isEnabled = 1 and scopeContent = 1 " +
            "AND (scope LIKE '%' || ? || '%' or scope LIKE '%' || ? || '%' or scope is null or scope = '') " +
            "and (excludeScope is null or (excludeScope not LIKE '%' || ? || '%' and excludeScope not LIKE '%' || ? || '%')) " +
            "order by sortOrder"
        db.queryList(sql, listOf(name, origin, name, origin), ReplaceRule::class.java)
    }

    override fun findEnabledByTitleScope(name: String, origin: String): List<ReplaceRule> = withLock {
        val sql = "SELECT * FROM replace_rules WHERE isEnabled = 1 and scopeTitle = 1 " +
            "AND (scope LIKE '%' || ? || '%' or scope LIKE '%' || ? || '%' or scope is null or scope = '') " +
            "and (excludeScope is null or (excludeScope not LIKE '%' || ? || '%' and excludeScope not LIKE '%' || ? || '%')) " +
            "order by sortOrder"
        db.queryList(sql, listOf(name, origin, name, origin), ReplaceRule::class.java)
    }

    override fun getByGroup(group: String): List<ReplaceRule> = withLock {
        db.queryList("select * from replace_rules where `group` like '%' || ? || '%'", listOf(group), ReplaceRule::class.java)
    }

    override val noGroup: List<ReplaceRule> get() = withLock {
        db.queryList("select * from replace_rules where `group` is null or `group` = ''", emptyList(), ReplaceRule::class.java)
    }

    override val summary: Int get() = withLock {
        db.queryValue("SELECT COUNT(*) - SUM(isEnabled) FROM replace_rules", emptyList(), Int::class.java) ?: 0
    }

    override fun enableAll(enable: Boolean) {
        withLock {
            db.execute("UPDATE replace_rules SET isEnabled = ?", listOf(enable))
        }
    }

    override fun insert(vararg replaceRule: ReplaceRule): List<Long> = withLock {
        db.execute(
            "INSERT OR REPLACE INTO replace_rules (" +
                "id, name, `group`, pattern, replacement, scope, scopeTitle, scopeContent, " +
                "excludeScope, isEnabled, isRegex, timeoutMillisecond, sortOrder" +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
            replaceRule.flatMap { r ->
                listOf(
                    r.id, r.name, r.group, r.pattern, r.replacement, r.scope, r.scopeTitle,
                    r.scopeContent, r.excludeScope, r.isEnabled, r.isRegex, r.timeoutMillisecond, r.order
                )
            }
        )
        replaceRule.map { it.id }
    }

    override fun update(vararg replaceRules: ReplaceRule) {
        withLock {
            db.execute(
                "UPDATE replace_rules SET name=?, `group`=?, pattern=?, replacement=?, scope=?, " +
                    "scopeTitle=?, scopeContent=?, excludeScope=?, isEnabled=?, isRegex=?, " +
                    "timeoutMillisecond=?, sortOrder=? WHERE id=?",
                replaceRules.flatMap { r ->
                    listOf(
                        r.name, r.group, r.pattern, r.replacement, r.scope, r.scopeTitle,
                        r.scopeContent, r.excludeScope, r.isEnabled, r.isRegex,
                        r.timeoutMillisecond, r.order, r.id
                    )
                }
            )
        }
    }

    override fun delete(vararg replaceRules: ReplaceRule) {
        withLock {
            db.execute("DELETE FROM replace_rules WHERE id = ?", replaceRules.map { it.id })
        }
    }

    // ---- 内部 SQL 片段（对照 Legado REPLACE_RULE_GROUP_FILTER / NO_GROUP_FILTER） ----

    private fun replaceRuleGroupFilter(): String =
        "trim(:groupName, $GROUP_TRIM_CHARACTERS) <> ''\n" +
            "and exists (\n" +
            "    with recursive replace_rule_groups(group_name, rest) as (\n" +
            "        select '',\n" +
            "            replace(replace(replace(coalesce(t2.`group`, ''), ';', ','), '，', ','), '；', ',') || ','\n" +
            "        union all\n" +
            "        select\n" +
            "            trim(substr(rest, 1, instr(rest, ',') - 1), $GROUP_TRIM_CHARACTERS),\n" +
            "            substr(rest, instr(rest, ',') + 1)\n" +
            "        from replace_rule_groups\n" +
            "        where rest <> ''\n" +
            "    )\n" +
            "    select 1\n" +
            "    from replace_rule_groups\n" +
            "    where group_name = trim(:groupName, $GROUP_TRIM_CHARACTERS)\n" +
            ")"

    private fun noGroupFilter(): String =
        "trim(coalesce(`group`, ''), $GROUP_TRIM_CHARACTERS) in ('', '未分组')"

    companion object {
        private const val GROUP_TRIM_CHARACTERS =
            "char(9,10,11,12,13,28,29,30,31,32,160,5760,8192,8193,8194,8195,8196," +
                "8197,8198,8199,8200,8201,8202,8232,8233,8239,8287,12288)"
    }
}
