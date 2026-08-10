package io.legado.desktop.data.dao

import io.legado.desktop.data.entities.ReadRecord
import io.legado.desktop.data.entities.ReadRecordAuthors
import io.legado.desktop.data.entities.ReadRecordBook
import io.legado.desktop.data.entities.ReadRecordShow
import kotlinx.coroutines.flow.Flow

interface ReadRecordDao {

val all: List<ReadRecord>

    
    fun flowBooks(): Flow<List<ReadRecordBook>>

    
    val allShow: List<ReadRecordShow>

val allTime: Long

    
    fun search(searchKey: String): List<ReadRecordShow>

    fun getReadTime(bookName: String): Long?

    fun getRecord(deviceId: String, bookName: String): ReadRecord?

    fun getAuthor(deviceId: String, bookName: String): String?

    fun insertRaw(vararg readRecord: ReadRecord)

    fun insert(vararg readRecord: ReadRecord) {
        readRecord.forEach { record ->
            val author = ReadRecordAuthors.merge(
                getAuthor(record.deviceId, record.bookName).orEmpty(),
                record.author,
            )
            insertRaw(record.copy(author = author))
        }
    }

    fun update(vararg record: ReadRecord)

    fun delete(vararg record: ReadRecord)

    fun clear()

    fun deleteByName(bookName: String)
}
