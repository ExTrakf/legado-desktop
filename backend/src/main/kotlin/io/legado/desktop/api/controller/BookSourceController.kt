package io.legado.desktop.api.controller


import io.legado.desktop.api.ReturnData
import io.legado.desktop.data.appDb
import io.legado.desktop.data.entities.BookSource
import io.legado.desktop.help.DefaultData
import io.legado.desktop.help.config.AppConfig
import io.legado.desktop.help.source.SourceHelp
import io.legado.desktop.model.jsSource.JsSourceUpsert
import io.legado.desktop.model.jsSource.JsSourceUpsert.PayloadIssue
import io.legado.desktop.utils.GSON
import io.legado.desktop.utils.fromJsonArray
import io.legado.desktop.utils.fromJsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import okio.ByteString.Companion.encodeUtf8
import java.security.MessageDigest

object BookSourceController {

    private const val JS_SOURCE_SAVE_TIMEOUT_MILLIS = 30_000L
    private const val JS_SOURCE_TOKEN_HEADER = "x-legado-token"
    private const val JS_SOURCE_WEBSOCKET_PROTOCOL_HEADER = "sec-websocket-protocol"
    private const val JS_SOURCE_WEBSOCKET_PROTOCOL = "legado"
    private const val JS_SOURCE_WEBSOCKET_PROTOCOL_PREFIX = "legado.token."

    val sources: ReturnData
        get() {
            val bookSources = appDb.bookSourceDao.all
            val returnData = ReturnData()
            return if (bookSources.isEmpty()) {
                returnData.setErrorMsg("设备源列表为空")
            } else returnData.setData(bookSources)
        }

    fun saveSource(postData: String?): ReturnData {
        val returnData = ReturnData()
        postData ?: return returnData.setErrorMsg("数据不能为空")
        val bookSource = GSON.fromJsonObject<BookSource>(postData).getOrNull()
        if (bookSource != null) {
            if (bookSource.bookSourceName.isNullOrEmpty() || bookSource.bookSourceUrl.isNullOrEmpty()) {
                returnData.setErrorMsg("源名称和URL不能为空")
            } else {
                appDb.bookSourceDao.insert(bookSource)
                returnData.setData("")
            }
        } else {
            returnData.setErrorMsg("转换源失败")
        }
        return returnData
    }

    fun saveSources(postData: String?): ReturnData {
        postData ?: return ReturnData().setErrorMsg("数据为空")
        val okSources = arrayListOf<BookSource>()
        val bookSources = GSON.fromJsonArray<BookSource>(postData).getOrNull()
        if (bookSources.isNullOrEmpty()) {
            return ReturnData().setErrorMsg("转换源失败")
        }
        bookSources.forEach { bookSource ->
            if (bookSource.bookSourceName.isNotBlank()
                && bookSource.bookSourceUrl.isNotBlank()
            ) {
                appDb.bookSourceDao.insert(bookSource)
                okSources.add(bookSource)
            }
        }
        return ReturnData().setData(okSources)
    }

    suspend fun saveJsSource(
        postData: String?,
        openedSourceUrl: String? = null,
    ): ReturnData {
        val returnData = ReturnData()
        when (JsSourceUpsert.validatePayload(postData)) {
            PayloadIssue.EMPTY -> return returnData.setErrorMsg("数据不能为空")
            PayloadIssue.TOO_LARGE -> return returnData.setErrorMsg("JS源脚本不能超过 1 MiB")
            null -> Unit
        }
        return try {
            val source = JsSourceUpsert.save(
                requireNotNull(postData),
                openedSourceUrl = openedSourceUrl,
                timeoutMillis = JS_SOURCE_SAVE_TIMEOUT_MILLIS,
            )
            returnData.setData(source)
        } catch (error: TimeoutCancellationException) {
            returnData.setErrorMsg("JS源保存超时")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            returnData.setErrorMsg(error.localizedMessage ?: "JS源保存失败")
        }
    }

    fun validateJsSourceRequest(
        headers: Map<String, String>,
        configuredToken: String? = AppConfig.jsSourceApiToken,
    ): ReturnData? {
        val returnData = ReturnData()
        if (!hasValidJsSourceApiToken(headers, configuredToken)) {
            return returnData.setErrorMsg("Web 书源访问令牌未配置或不正确")
        }
        if (headers.header("transfer-encoding") != null) {
            return returnData.setErrorMsg("JS源请求不支持 Transfer-Encoding")
        }
        val contentType = headers.header("content-type")
            ?.substringBefore(';')
            ?.trim()
        if (!contentType.equals("text/plain", ignoreCase = true)) {
            return returnData.setErrorMsg("JS源请求 Content-Type 必须为 text/plain")
        }
        val contentLength = headers.header("content-length")?.trim()?.toLongOrNull()
            ?: return returnData.setErrorMsg("JS源请求必须提供 Content-Length")
        if (contentLength < 0 || contentLength > JsSourceUpsert.MAX_SOURCE_BYTES) {
            return returnData.setErrorMsg("JS源脚本不能超过 1 MiB")
        }
        return null
    }

    fun hasValidJsSourceApiToken(
        headers: Map<String, String>,
        configuredToken: String? = AppConfig.jsSourceApiToken,
    ): Boolean {
        return matchesJsSourceApiToken(configuredToken, headers.header(JS_SOURCE_TOKEN_HEADER))
    }

    fun hasValidJsSourceWebSocketProtocol(
        headers: Map<String, String>,
        configuredToken: String? = AppConfig.jsSourceApiToken,
    ): Boolean {
        val expected = jsSourceWebSocketProtocol(configuredToken) ?: return false
        val protocols = headers.header(JS_SOURCE_WEBSOCKET_PROTOCOL_HEADER)
            ?.split(',')
            ?.map(String::trim)
            ?: return false
        if (protocols.size != 2 || protocols.first() != JS_SOURCE_WEBSOCKET_PROTOCOL) {
            return false
        }
        val actual = protocols.last()
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            actual.toByteArray(Charsets.UTF_8),
        )
    }

    internal fun jsSourceWebSocketProtocol(token: String?): String? {
        val normalizedToken = token?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val encodedToken = normalizedToken.encodeUtf8().base64Url().trimEnd('=')
        return JS_SOURCE_WEBSOCKET_PROTOCOL_PREFIX + encodedToken
    }

    internal fun matchesJsSourceApiToken(expected: String?, actual: String?): Boolean {
        if (expected.isNullOrBlank() || actual == null) return false
        return MessageDigest.isEqual(
            expected.toByteArray(Charsets.UTF_8),
            actual.toByteArray(Charsets.UTF_8),
        )
    }

    private fun Map<String, String>.header(name: String): String? {
        return entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    }

    fun getSource(parameters: Map<String, List<String>>): ReturnData {
        val url = parameters["url"]?.firstOrNull()
        val returnData = ReturnData()
        if (url.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数url不能为空，请指定源地址")
        }
        val bookSource = appDb.bookSourceDao.getBookSource(url)
            ?: return returnData.setErrorMsg("未找到源，请检查书源地址")
        return returnData.setData(bookSource)
    }

    fun deleteSources(postData: String?): ReturnData {
        kotlin.runCatching {
            GSON.fromJsonArray<BookSource>(postData).getOrThrow().let {
                SourceHelp.deleteBookSources(it)
            }
        }.onFailure {
            return ReturnData().setErrorMsg(it.localizedMessage ?: "数据格式错误")
        }
        return ReturnData().setData("已执行"/*okSources*/)
    }

    /**
     * 恢复默认数据（原版由 UI"恢复默认"按钮触发，桌面版暴露 API）。
     * body 可传 {"types": ["txtTocRule","dictRule","rssSource","httpTTS"]}；缺省 = 全部恢复。
     */
    fun restoreDefaultData(postData: String?): ReturnData {
        val types = kotlin.runCatching {
            GSON.fromJsonObject<Map<String, List<String>>>(postData)
                .getOrNull()?.get("types")
        }.getOrNull().orEmpty().toHashSet()
        val all = types.isEmpty()
        if (all || "txtTocRule" in types) DefaultData.importDefaultTocRules()
        if (all || "dictRule" in types) DefaultData.importDefaultDictRules()
        if (all || "rssSource" in types) DefaultData.importDefaultRssSources()
        if (all || "httpTTS" in types) DefaultData.importDefaultHttpTTS()
        return ReturnData().setData("恢复默认完成")
    }
}
