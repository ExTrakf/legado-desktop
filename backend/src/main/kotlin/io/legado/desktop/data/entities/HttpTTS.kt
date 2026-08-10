package io.legado.desktop.data.entities

import com.jayway.jsonpath.DocumentContext
import io.legado.desktop.utils.GSON
import io.legado.desktop.utils.jsonPath
import io.legado.desktop.utils.readLong
import io.legado.desktop.utils.readString

/**
 * 在线朗读引擎
 */
data class HttpTTS(
    val id: Long = System.currentTimeMillis(),
    var name: String = "",
    var url: String = "",
    var contentType: String? = null,
    var pauseDuration: Int = 0,
    override var concurrentRate: String? = "0",
    override var loginUrl: String? = null,
    override var loginUi: String? = null,
    override var header: String? = null,
    override var jsLib: String? = null,
    override var enabledCookieJar: Boolean? = false,
    var loginCheckJs: String? = null,
    var lastUpdateTime: Long = System.currentTimeMillis()
) : BaseSource {

    override fun getTag(): String {
        return name
    }

    override fun getKey(): String {
        return "httpTts:$id"
    }

    fun equal(source: HttpTTS): Boolean {
        return name == source.name &&
                url == source.url &&
                contentType == source.contentType &&
                pauseDuration == source.pauseDuration &&
                concurrentRate == source.concurrentRate &&
                loginUrl == source.loginUrl &&
                loginUi == source.loginUi &&
                header == source.header &&
                jsLib == source.jsLib &&
                enabledCookieJar == source.enabledCookieJar &&
                loginCheckJs == source.loginCheckJs
    }

    @Suppress("MemberVisibilityCanBePrivate")
    companion object {

        fun fromJsonDoc(doc: DocumentContext): Result<HttpTTS> {
            return kotlin.runCatching {
                val loginUi = doc.read<Any>("$.loginUi")
                HttpTTS(
                    id = doc.readLong("$.id") ?: System.currentTimeMillis(),
                    name = doc.readString("$.name")!!,
                    url = doc.readString("$.url")!!,
                    contentType = doc.readString("$.contentType"),
                    pauseDuration = doc.readLong("$.pauseDuration")
                        ?.coerceIn(0L, 10_000L)
                        ?.toInt()
                        ?: 0,
                    concurrentRate = doc.readString("$.concurrentRate"),
                    loginUrl = doc.readString("$.loginUrl"),
                    loginUi = if (loginUi is List<*>) GSON.toJson(loginUi) else loginUi?.toString(),
                    header = doc.readString("$.header"),
                    loginCheckJs = doc.readString("$.loginCheckJs"),
                    lastUpdateTime = doc.readLong("$.lastUpdateTime") ?: System.currentTimeMillis(),
                    jsLib = doc.readString("$.jsLib")
                )
            }
        }

        fun fromJson(json: String): Result<HttpTTS> {
            return fromJsonDoc(jsonPath.parse(json))
        }

        fun fromJsonArray(jsonArray: String): Result<ArrayList<HttpTTS>> {
            return kotlin.runCatching {
                val sources = arrayListOf<HttpTTS>()
                val doc = jsonPath.parse(jsonArray).read<List<*>>("$")
                doc.forEach {
                    val jsonItem = jsonPath.parse(it)
                    fromJsonDoc(jsonItem).getOrThrow().let { source ->
                        sources.add(source)
                    }
                }
                return@runCatching sources
            }
        }

    }

}
