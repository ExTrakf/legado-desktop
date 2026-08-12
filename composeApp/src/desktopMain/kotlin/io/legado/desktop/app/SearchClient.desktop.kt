package io.legado.desktop.app

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.time.Duration
import java.util.Base64
import java.util.concurrent.CompletionStage

/**
 * 搜索客户端（desktop actual）。
 *
 * JDK 内置 java.net.http.WebSocket 连接后端 WS /searchBook：
 * - 握手子协议固定 [legado, legado.token.<base64url(token)>]（后端 JS_SOURCE_WEBSOCKET_PROTOCOL_PREFIX）
 * - 连上后发送 {"key":"<key>"}
 * - 每个结果帧（JSON 数组）回调 onResult；结束/关闭/异常回调 onDone
 *
 * 注意：onResult/onDone 在 WS IO 线程被回调，调用方（Compose 页面）必须自行
 * marshal 回 UI 线程（如用 rememberCoroutineScope().launch 更新状态）。
 */
actual class SearchClient actual constructor(
    private val baseUrl: String,
    private val token: String,
) {
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    actual suspend fun search(key: String, onResult: (String) -> Unit, onDone: () -> Unit) =
        withContext(Dispatchers.IO) {
            val done = CompletableDeferred<Unit>()
            val wsUrl = wsSearchUrl(baseUrl)
            val offeredProtocol = "legado.token.${base64Url(token.trim())}"
            val ws = try {
                http.newWebSocketBuilder()
                    .subprotocols("legado", offeredProtocol)
                    .buildAsync(
                        URI.create(wsUrl),
                        object : WebSocket.Listener {
                            override fun onText(
                                webSocket: WebSocket,
                                data: CharSequence,
                                last: Boolean,
                            ): CompletionStage<*>? {
                                if (data.length > 0) onResult(data.toString())
                                webSocket.request(1)
                                return null
                            }

                            override fun onClose(
                                webSocket: WebSocket,
                                statusCode: Int,
                                reason: String,
                            ): CompletionStage<*>? {
                                done.complete(Unit)
                                return null
                            }

                            override fun onError(webSocket: WebSocket, error: Throwable) {
                                done.complete(Unit)
                            }
                        },
                    ).join()
            } catch (e: Exception) {
                onDone()
                return@withContext
            }
            val payload = """{"key":"${escapeJson(key)}"}"""
            ws.sendText(payload, true).join()
            done.await()
            onDone()
        }

    /** 由 baseUrl（http 端口）推导 WS 搜索地址：scheme http(s)->ws(s)，端口 +1，路径 /searchBook */
    private fun wsSearchUrl(baseUrl: String): String {
        val uri = URI.create(baseUrl)
        val scheme = if (uri.scheme == "https") "wss" else "ws"
        val port = uri.port
        val wsPort = if (port == -1) {
            if (uri.scheme == "https") 443 else 80
        } else {
            port + 1
        }
        return "$scheme://${uri.host}:$wsPort/searchBook"
    }

    private fun base64Url(s: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray(Charsets.UTF_8))

    private fun escapeJson(s: String): String =
        buildString {
            s.forEach { c ->
                when (c) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(c)
                }
            }
        }
}
