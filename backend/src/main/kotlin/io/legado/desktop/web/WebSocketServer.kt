package io.legado.desktop.web

import fi.iki.elonen.NanoWSD
import io.legado.desktop.api.controller.BookSourceController
import io.legado.desktop.web.socket.BookSearchWebSocket
import io.legado.desktop.web.socket.BookSourceDebugWebSocket
import io.legado.desktop.web.socket.RssSourceDebugWebSocket

/**
 * WebSocket 服务（NanoWSD，与 Legado 一致）。
 * 监听端口 = HTTP 端口 + 1。
 */
class WebSocketServer(serverPort: Int) : NanoWSD(serverPort) {

    override fun serve(session: IHTTPSession): Response {
        if (isWebsocketRequested(session)) {
            if (session.uri !in WEBSOCKET_ROUTES) {
                return newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "text/plain; charset=utf-8",
                    "WebSocket route not found"
                ).apply { addHeader("X-Content-Type-Options", "nosniff") }
            }
            if (!BookSourceController.hasValidJsSourceWebSocketProtocol(session.headers)) {
                return newFixedLengthResponse(
                    Response.Status.FORBIDDEN,
                    "text/plain; charset=utf-8",
                    "Web 书源访问令牌未配置或不正确"
                ).apply {
                    addHeader("Cache-Control", "no-store")
                    addHeader("X-Content-Type-Options", "nosniff")
                }
            }
        }
        return super.serve(session)
    }

    override fun openWebSocket(handshake: IHTTPSession): WebSocket? {
        return when (handshake.uri) {
            "/bookSourceDebug" -> {
                BookSourceDebugWebSocket(handshake)
            }
            "/rssSourceDebug" -> {
                RssSourceDebugWebSocket(handshake)
            }
            "/searchBook" -> {
                BookSearchWebSocket(handshake)
            }
            else -> null
        }
    }

    companion object {
        private val WEBSOCKET_ROUTES = setOf(
            "/bookSourceDebug",
            "/rssSourceDebug",
            "/searchBook",
        )
    }

}
