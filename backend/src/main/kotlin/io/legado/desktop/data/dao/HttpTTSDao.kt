package io.legado.desktop.data.dao

import io.legado.desktop.data.entities.HttpTTS
import kotlinx.coroutines.flow.Flow

interface HttpTTSDao {

val all: List<HttpTTS>

    fun flowAll(): Flow<List<HttpTTS>>

val count: Int

    fun get(id: Long): HttpTTS?

    fun getName(id: Long): String?

    fun insert(vararg httpTTS: HttpTTS)

    fun delete(vararg httpTTS: HttpTTS)

    fun update(vararg httpTTS: HttpTTS)

    fun deleteDefault()
}