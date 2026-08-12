package io.legado.desktop.app

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonElement

/** 后端 ReturnData 包装（{isSuccess, errorMsg, data}） */
@Serializable
data class ReturnData(
    val isSuccess: Boolean = false,
    val errorMsg: String? = null,
)

@Serializable
data class Book(
    val bookUrl: String = "",
    val name: String = "",
    val author: String = "",
    val tocUrl: String? = null,
    val origin: String = "",
    val originName: String = "",
    val coverUrl: String? = null,
    val durChapterIndex: Int = 0,
    val durChapterTitle: String? = null,
    val durChapterPos: Int = 0,
    val totalChapterNum: Int = 0,
    val type: Int = 0,
    val group: String? = null,
)

@Serializable
data class BookSource(
    val bookSourceUrl: String = "",
    val bookSourceName: String = "",
    val bookSourceGroup: String? = null,
    val bookSourceType: Int = 0,
    val enabled: Boolean = false,
    val customOrder: Int = 0,
    val lastUpdateTime: Long = 0,
)

@Serializable
data class BookChapter(
    val url: String = "",
    val title: String = "",
    val index: Int = 0,
    val bookUrl: String = "",
    val isVolume: Boolean = false,
)

private val json = Json { ignoreUnknownKeys = true }

/** 解析 ReturnData.data 为 T */
fun <T> parseData(raw: String, serializer: KSerializer<T>): T? {
    return try {
        val obj = json.parseToJsonElement(raw).jsonObject
        if (obj["isSuccess"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == false) {
            null
        } else {
            obj["data"]?.let { json.decodeFromJsonElement(serializer, it) }
        }
    } catch (_: Exception) {
        null
    }
}

/** 解析 ReturnData.data 为原始 JsonElement（正文/动态结构用） */
fun parseDataRaw(raw: String): JsonElement? {
    return try {
        val obj = json.parseToJsonElement(raw).jsonObject
        if (obj["isSuccess"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() == false) {
            null
        } else {
            obj["data"]
        }
    } catch (_: Exception) {
        null
    }
}
