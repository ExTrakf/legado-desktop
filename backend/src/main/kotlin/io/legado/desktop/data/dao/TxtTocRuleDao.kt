package io.legado.desktop.data.dao

import io.legado.desktop.data.entities.TxtTocRule
import kotlinx.coroutines.flow.Flow

interface TxtTocRuleDao {

    fun observeAll(): Flow<List<TxtTocRule>>

val all: List<TxtTocRule>

val enabled: List<TxtTocRule>

val disabled: List<TxtTocRule>

val count: Int

    fun get(id: Long): TxtTocRule?

val minOrder: Int

val maxOrder: Int

    fun insert(vararg rule: TxtTocRule)

    fun update(vararg rule: TxtTocRule)

    fun delete(vararg rule: TxtTocRule)

    fun deleteDefault()
}