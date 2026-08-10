package io.legado.desktop.data.dao

import io.legado.desktop.data.entities.RssStar
import kotlinx.coroutines.flow.Flow

interface RssStarDao {

val all: List<RssStar>

    fun flowGroups(): Flow<List<String>>

    fun flowByGroup(group: String): Flow<List<RssStar>>

    fun get(origin: String, link: String): RssStar?

    fun liveAll(): Flow<List<RssStar>>

    fun insert(vararg rssStar: RssStar)

    fun update(vararg rssStar: RssStar)

    fun updateOrigin(origin: String, oldOrigin: String)

    fun delete(origin: String)

    fun delete(origin: String, link: String)

    fun deleteByGroup(group: String)

    fun deleteAll()
}