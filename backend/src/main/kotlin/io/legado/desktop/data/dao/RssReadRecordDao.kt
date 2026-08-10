package io.legado.desktop.data.dao

import io.legado.desktop.data.entities.RssReadRecord

interface RssReadRecordDao {

    fun insertRecord(vararg rssReadRecord: RssReadRecord)

    fun getRecords(): List<RssReadRecord>

    fun getRecordsByOrigin(origin: String): List<RssReadRecord>

    fun getRecord( record: String, origin: String): RssReadRecord?

    fun update(vararg rssRecord: RssReadRecord)

val countRecords: Int

    fun countRecordsByOrigin(origin: String): Int

    fun deleteAllRecord()

    fun deleteRecordsByOrigin(origin: String)

}