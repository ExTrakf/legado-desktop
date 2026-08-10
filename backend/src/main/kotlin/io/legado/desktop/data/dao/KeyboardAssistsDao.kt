package io.legado.desktop.data.dao

import io.legado.desktop.data.entities.KeyboardAssist
import kotlinx.coroutines.flow.Flow

interface KeyboardAssistsDao {

val all: List<KeyboardAssist>

    fun getByType(type: Int): List<KeyboardAssist>

val flowAll: Flow<List<KeyboardAssist>>

    fun flowByType(type: Int): Flow<List<KeyboardAssist>>

val maxSerialNo: Int

    fun insert(vararg keyboardAssist: KeyboardAssist)

    fun update(vararg keyboardAssist: KeyboardAssist)

    fun delete(vararg keyboardAssist: KeyboardAssist)

    suspend fun deleteAll()

}