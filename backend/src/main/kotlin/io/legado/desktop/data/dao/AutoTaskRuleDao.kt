package io.legado.desktop.data.dao

import io.legado.desktop.data.entities.AutoTaskRule
import kotlinx.coroutines.flow.Flow

interface AutoTaskRuleDao {

    fun all(): List<AutoTaskRule>

    fun flowAll(): Flow<List<AutoTaskRule>>

    fun getById(id: String): AutoTaskRule?

    fun maxOrder(): Int

        fun upsert(vararg rules: AutoTaskRule)

    fun update(vararg rules: AutoTaskRule)

    fun deleteByIds(ids: Collection<String>)

    fun updateCron(ids: Collection<String>, cron: String): Int

    fun updateEnabled(ids: Collection<String>, enabled: Boolean): Int

    
    fun clearRunLog(id: String): Int

    
    fun updateRunState(
        id: String,
        lastRunAt: Long,
        lastResult: String?,
        lastError: String?,
        lastLog: String?
    )
}
