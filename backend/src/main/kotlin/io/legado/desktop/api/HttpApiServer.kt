package io.legado.desktop.api

import fi.iki.elonen.NanoHTTPD
import java.io.IOException

/**
 * HTTP API 服务（NanoHTTPD，与 Legado 一致）。
 *
 * 前后端分离：所有响应带 CORS 头，前端可跨域开发。
 * 契约见 docs/API.md。
 */
class HttpApiServer(
    private val host: String,
    private val port: Int,
) : NanoHTTPD(host, port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method
        return when {
            method == Method.OPTIONS -> cors(emptyResponse()) // CORS 预检
            method == Method.GET && uri == "/api/health" -> health()
            else -> cors(
                newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "application/json; charset=utf-8",
                    """{"isSuccess":false,"errorMsg":"not found: $method $uri","data":null}"""
                )
            )
        }
    }

    private fun health(): Response {
        val body = """{"isSuccess":true,"errorMsg":"","data":{"service":"legado-desktop-backend","version":"0.1.0","status":"ok"}}"""
        return cors(
            newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", body)
        )
    }

    private fun cors(response: Response): Response {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "content-type, x-legado-token")
        response.addHeader("Cache-Control", "no-store")
        return response
    }

    private fun emptyResponse(): Response =
        newFixedLengthResponse(Response.Status.OK, "text/plain; charset=utf-8", "")

    override fun start() {
        try {
            super.start(SOCKET_READ_TIMEOUT, false)
        } catch (e: IOException) {
            throw IllegalStateException("无法监听 $host:$port，端口可能被占用", e)
        }
    }

    companion object {
        private const val SOCKET_READ_TIMEOUT = 60_000
    }
}
