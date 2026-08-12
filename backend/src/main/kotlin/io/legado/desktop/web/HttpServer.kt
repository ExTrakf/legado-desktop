package io.legado.desktop.web

import fi.iki.elonen.NanoHTTPD
import io.legado.desktop.api.ReturnData
import io.legado.desktop.api.controller.BookController
import io.legado.desktop.api.controller.BookSourceController
import io.legado.desktop.api.controller.HttpLogController
import io.legado.desktop.api.controller.ReplaceRuleController
import io.legado.desktop.api.controller.RssSourceController
import io.legado.desktop.help.coroutine.Coroutine
import io.legado.desktop.utils.GSON
import io.legado.desktop.utils.LogUtils
import io.legado.desktop.utils.stackTraceStr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okio.Pipe
import okio.buffer
import java.io.ByteArrayInputStream

/**
 * HTTP API 服务（NanoHTTPD，与 Legado 一致）。
 *
 * 前后端分离：所有响应带 CORS 头，前端可跨域开发。
 * 契约见 docs/API.md。
 *
 * 与原版差异（等价裁剪）：
 * - 静态资源伺服（AssetsWeb，官方 web UI）不移植：未知路由返回 JSON 404
 * - 评论相关路由（legacyReview/openReview/getReview*）不移植（ReviewController 未迁移）
 * - Bitmap 图片响应裁剪：T6.2 封面/图片实现后恢复（响应分支保留 ByteArray 兼容）
 * - WebService.serve()（Android 前台服务）无对应物，删除
 */
class HttpServer(port: Int) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        var returnData: ReturnData? = null
        var shouldCloseConnection = false
        val ct = ContentType(session.headers["content-type"]).tryUTF8()
        session.headers["content-type"] = ct.contentTypeHeader
        var uri = session.uri

        val startAt = System.currentTimeMillis()
        LogUtils.d(TAG) {
            "${session.method.name} - $uri - ${session.queryParameterString} - Start($startAt)"
        }

        try {
            when (session.method) {
                Method.OPTIONS -> {
                    val response = newFixedLengthResponse(
                        Response.Status.OK,
                        "text/plain; charset=utf-8",
                        ""
                    )
                    response.addHeader("Access-Control-Allow-Methods", "GET, POST")
                    response.addHeader(
                        "Access-Control-Allow-Headers",
                        "content-type, x-legado-token"
                    )
                    response.addWebHeaders(session.headers["origin"], uri)
                    //response.addHeader("Access-Control-Max-Age", "3600");
                    return response
                }

                Method.POST -> {
                    val requestError = when {
                        uri == "/saveJsSource" -> {
                            BookSourceController.validateJsSourceRequest(session.headers)
                        }

                        uri in PROTECTED_SOURCE_WRITE_ROUTES &&
                            !BookSourceController.hasValidJsSourceApiToken(session.headers) -> {
                            ReturnData().setErrorMsg("Web 书源访问令牌未配置或不正确")
                        }

                        else -> null
                    }
                    if (requestError != null) {
                        returnData = requestError
                        shouldCloseConnection = true
                    } else {
                        val files = HashMap<String, String>()
                        session.parseBody(files)
                        val postData = files["postData"]

                        returnData = runBlocking {
                            when (uri) {
                                "/saveBookSource" -> BookSourceController.saveSource(postData)
                                "/saveBookSources" -> BookSourceController.saveSources(postData)
                                "/saveJsSource" -> BookSourceController.saveJsSource(
                                    postData,
                                    session.parameters["openedSourceUrl"]?.firstOrNull(),
                                )
                                "/deleteBookSources" -> BookSourceController.deleteSources(postData)
                                "/saveBook" -> BookController.saveBook(postData)
                                "/deleteBook" -> BookController.deleteBook(postData)
                                "/saveBookProgress" -> BookController.saveBookProgress(postData)
                                "/cacheBook" -> BookController.cacheBook(postData)
                                "/cacheBookStop" -> BookController.cacheBookStop()
                                "/cacheBookRemove" -> BookController.cacheBookRemove(postData)
                                "/addLocalBook" -> BookController.addLocalBook(
                                    session.parameters,
                                    files,
                                )
                                "/saveReadConfig" -> BookController.saveWebReadConfig(postData)
                                "/saveRssSource" -> RssSourceController.saveSource(postData)
                                "/saveRssSources" -> RssSourceController.saveSources(postData)
                                "/deleteRssSources" -> RssSourceController.deleteSources(postData)
                                "/saveReplaceRule" -> ReplaceRuleController.saveRule(postData)
                                "/deleteReplaceRule" -> ReplaceRuleController.delete(postData)
                                "/testReplaceRule" -> ReplaceRuleController.testRule(postData)
                                "/restoreDefaultData" -> BookSourceController.restoreDefaultData(postData)
                                else -> null
                            }
                        }
                    }
                }

                Method.GET -> {
                    val parameters = session.parameters
                    val requestError = if (
                        uri in PROTECTED_HTTP_LOG_READ_ROUTES &&
                        !BookSourceController.hasValidJsSourceApiToken(session.headers)
                    ) {
                        ReturnData().setErrorMsg("Web 书源访问令牌未配置或不正确")
                    } else {
                        null
                    }
                    if (requestError != null) {
                        returnData = requestError
                        shouldCloseConnection = true
                    } else {
                        returnData = when (uri) {
                            "/api/health" -> health()
                            "/getBookSource" -> BookSourceController.getSource(parameters)
                            "/getBookSources" -> BookSourceController.sources
                            "/getHttpLogs" -> HttpLogController.getLogs(parameters)
                            "/getHttpLog" -> HttpLogController.getLog(parameters)
                            "/getBookshelf" -> BookController.bookshelf
                            "/getChapterList" -> BookController.getChapterList(parameters)
                            "/refreshToc" -> BookController.refreshToc(parameters)
                            "/getBookContent" -> BookController.getBookContent(parameters)
                            "/cover" -> BookController.getCover(parameters)
                            "/image" -> BookController.getImg(parameters)
                            "/getReadConfig" -> BookController.getWebReadConfig()
                            "/getRssSource" -> RssSourceController.getSource(parameters)
                            "/getRssSources" -> RssSourceController.sources
                            "/getReplaceRules" -> ReplaceRuleController.allRules
                            else -> null
                        }
                    }
                }

                else -> Unit
            }

            if (returnData == null) {
                // 原版回退到 AssetsWeb 静态资源；桌面版前后端分离，未知路由一律 404
                return newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    JSON_MIME,
                    """{"isSuccess":false,"errorMsg":"not found: $uri","data":null}"""
                ).apply {
                    addWebHeaders(session.headers["origin"], uri)
                }
            }

            val response = when (val data = returnData.data) {
                is ByteArray -> {
                    newFixedLengthResponse(
                        Response.Status.OK,
                        "application/octet-stream",
                        ByteArrayInputStream(data),
                        data.size.toLong()
                    )
                }

                is List<*> -> {
                    if (data.size > 3000) {
                        val pipe = Pipe(16 * 1024)
                        Coroutine.async {
                            pipe.sink.buffer().outputStream().bufferedWriter(Charsets.UTF_8).use {
                                GSON.toJson(returnData, it)
                            }
                        }
                        newChunkedResponse(
                            Response.Status.OK,
                            JSON_MIME,
                            pipe.source.buffer().inputStream()
                        )
                    } else {
                        newFixedLengthResponse(
                            Response.Status.OK,
                            JSON_MIME,
                            GSON.toJson(returnData)
                        )
                    }
                }

                else -> newFixedLengthResponse(
                    Response.Status.OK,
                    JSON_MIME,
                    GSON.toJson(returnData)
                )
            }
            response.addHeader("Access-Control-Allow-Methods", "GET, POST")
            response.addWebHeaders(session.headers["origin"], uri)
            if (shouldCloseConnection) {
                response.closeConnection(true)
            }
            LogUtils.d(TAG) {
                "${session.method.name} - $uri - End($startAt)"
            }
            return response
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogUtils.d(TAG) {
                "${session.method.name} - $uri - Error End($startAt)\n$e\n${e.stackTraceStr}"
            }
            return newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "text/plain; charset=utf-8",
                e.message ?: "Internal server error"
            ).apply {
                addWebHeaders(session.headers["origin"], uri)
                closeConnection(true)
            }
        }
    }

    private fun health(): ReturnData {
        return ReturnData().setData(
            mapOf(
                "service" to "legado-desktop-backend",
                "version" to "0.1.0",
                "status" to "ok",
            )
        )
    }

    companion object {
        private const val TAG = "HttpServer"
        private const val JSON_MIME = "application/json; charset=utf-8"
        private val PROTECTED_SOURCE_WRITE_ROUTES = setOf(
            "/saveBookSource",
            "/saveBookSources",
            "/deleteBookSources",
            "/saveRssSource",
            "/saveRssSources",
            "/deleteRssSources",
            "/saveReplaceRule",
            "/deleteReplaceRule",
            "/testReplaceRule",
            "/restoreDefaultData",
        )
        private val PROTECTED_HTTP_LOG_READ_ROUTES = setOf(
            "/getHttpLogs",
            "/getHttpLog",
        )
    }

    private fun Response.addWebHeaders(origin: String?, uri: String) {
        addHeader("X-Content-Type-Options", "nosniff")
        origin?.let { addHeader("Access-Control-Allow-Origin", it) }
        if (uri in PROTECTED_HTTP_LOG_READ_ROUTES) {
            addHeader("Cache-Control", "no-store")
        }
    }

}
