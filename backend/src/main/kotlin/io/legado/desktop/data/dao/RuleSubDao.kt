package io.legado.desktop.data.dao

import io.legado.desktop.data.entities.RuleSub
import kotlinx.coroutines.flow.Flow

interface RuleSubDao {

val all: List<RuleSub>

    fun flowAll(): Flow<List<RuleSub>>

val maxOrder: Int

    fun findByUrl(url: String): RuleSub?

    fun insert(vararg ruleSub: RuleSub)

    fun delete(vararg ruleSub: RuleSub)

    fun update(vararg ruleSub: RuleSub)
}