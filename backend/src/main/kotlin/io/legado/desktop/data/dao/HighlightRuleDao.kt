package io.legado.desktop.data.dao

import io.legado.desktop.data.entities.HighlightRule
import kotlinx.coroutines.flow.Flow

interface HighlightRuleDao {

val all: List<HighlightRule>

    fun flowAll(): Flow<List<HighlightRule>>

    fun findById(id: Long): HighlightRule?

    
    fun findEnabledByBook(name: String, origin: String): List<HighlightRule>

val minOrder: Int

val maxOrder: Int

    fun insert(vararg rule: HighlightRule): List<Long>

    fun update(vararg rule: HighlightRule)

    fun delete(vararg rule: HighlightRule)

    fun deleteAll()

    fun replaceAll(rules: List<HighlightRule>) {
        deleteAll()
        if (rules.isNotEmpty()) insert(*rules.toTypedArray())
    }
}
