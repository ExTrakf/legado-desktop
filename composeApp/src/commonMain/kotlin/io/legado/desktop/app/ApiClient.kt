package io.legado.desktop.app

/**
 * 后端 HTTP 客户端（expect；desktopMain 用 java.net.http 实现）。
 * 契约见 docs/API.md：REST JSON，baseUrl 默认 http://127.0.0.1:2323。
 */
expect class ApiClient(baseUrl: String) {

    var baseUrl: String

    /** GET 请求返回原始 JSON 文本（或抛异常） */
    suspend fun get(path: String): String
}
