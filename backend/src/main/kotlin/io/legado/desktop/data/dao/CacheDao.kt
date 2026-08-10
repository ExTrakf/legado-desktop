package io.legado.desktop.data.dao

import io.legado.desktop.data.entities.Cache

interface CacheDao {

    fun get(key: String): Cache?

    fun get(key: String, now: Long): String?

    fun insert(vararg cache: Cache)

    fun delete(key: String)

    
    fun deleteSourceVariables(key: String)

    fun clearDeadline(now: Long)

}