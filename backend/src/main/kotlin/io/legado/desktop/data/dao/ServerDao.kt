package io.legado.desktop.data.dao

import io.legado.desktop.data.entities.Server
import kotlinx.coroutines.flow.Flow

interface ServerDao {

    fun observeAll(): Flow<List<Server>>

val all: List<Server>

    fun get(id: Long): Server?

    fun insert(vararg server: Server)

    fun update(vararg server: Server)

    fun delete(vararg server: Server)

    fun delete(id: Long)

    fun deleteDefault()
}