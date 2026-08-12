package io.legado.desktop.app

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
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
    val group: Int = 0, // 后端为数字分组 id（非字符串）
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

/** 搜索结果（对齐后端 SearchBook，与 /saveBook 所需 Book 字段兼容） */
@Serializable
data class SearchResult(
    val bookUrl: String = "",
    val origin: String = "",
    val originName: String = "",
    val name: String = "",
    val author: String = "",
    val kind: String? = null,
    val coverUrl: String? = null,
    val intro: String? = null,
    val wordCount: String? = null,
    val latestChapterTitle: String? = null,
    val tocUrl: String = "",
    val time: Long = 0,
    val originOrder: Int = 0,
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

/** 解析 ReturnData 的 isSuccess/errorMsg（写请求反馈用） */
fun returnStatus(raw: String): Pair<Boolean, String?> {
    return try {
        val obj = json.parseToJsonElement(raw).jsonObject
        val ok = obj["isSuccess"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false
        val msg = obj["errorMsg"]?.jsonPrimitive?.contentOrNull
        ok to msg
    } catch (_: Exception) {
        false to null
    }
}

/** 解析 WS 搜索一帧（JSON 数组）为搜索结果列表 */
fun parseSearchResults(raw: String): List<SearchResult>? {
    return try {
        json.decodeFromString(ListSerializer(SearchResult.serializer()), raw)
    } catch (_: Exception) {
        null
    }
}

/** 序列化书源列表（启停切换用，POST /saveBookSources） */
fun encodeSourceList(sources: List<BookSource>): String =
    json.encodeToString(ListSerializer(BookSource.serializer()), sources)

/** 搜索结果加书架：构造 /saveBook 所需 Book JSON（字段对齐后端 Book.save 所需） */
fun bookForShelf(r: SearchResult): String {
    return json.encodeToString(
        buildJsonObject {
            put("name", r.name)
            put("author", r.author)
            put("bookUrl", r.bookUrl)
            put("tocUrl", r.tocUrl)
            put("origin", r.origin)
            put("originName", r.originName)
            put("type", 0)
            r.kind?.let { put("kind", it) }
            r.coverUrl?.let { put("coverUrl", it) }
            r.intro?.let { put("intro", it) }
            r.wordCount?.let { put("wordCount", it) }
            r.latestChapterTitle?.let { put("latestChapterTitle", it) }
        }
    )
}

/** 阅读进度保存：构造 /saveBookProgress 所需 BookProgress JSON */
fun progressForShelf(
    book: Book,
    chapterIndex: Int,
    chapterTitle: String?,
    pos: Int,
): String {
    return json.encodeToString(
        buildJsonObject {
            put("name", book.name)
            put("author", book.author)
            put("durChapterIndex", chapterIndex)
            put("durChapterPos", pos)
            put("durChapterTime", System.currentTimeMillis())
            chapterTitle?.let { put("durChapterTitle", it) }
        }
    )
}
