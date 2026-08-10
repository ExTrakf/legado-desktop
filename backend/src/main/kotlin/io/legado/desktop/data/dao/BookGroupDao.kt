package io.legado.desktop.data.dao

import io.legado.desktop.constant.BookType
import io.legado.desktop.data.entities.BookGroup
import kotlinx.coroutines.flow.Flow

interface BookGroupDao {

    fun getByID(id: Long): BookGroup?

    fun getByName(groupName: String): BookGroup?

    fun flowAll(): Flow<List<BookGroup>>

    
    val show: List<BookGroup>

    fun flowSelect(): Flow<List<BookGroup>>

val idsSum: Long

val maxOrder: Int

val all: List<BookGroup>

val canAddGroup: Boolean

    fun enableGroup(groupId: Long)

    fun getGroupNames(id: Long): List<String>

    fun insert(vararg bookGroup: BookGroup)

    fun update(vararg bookGroup: BookGroup)

    fun delete(vararg bookGroup: BookGroup)

    fun isInRules(id: Long): Boolean {
        if (id < 0) {
            return true
        }
        return id and (id - 1) == 0L
    }

    fun getUnusedId(): Long {
        var id = 1L
        val idsSum = idsSum
        while (id and idsSum != 0L) {
            id = id.shl(1)
        }
        return id
    }
}
