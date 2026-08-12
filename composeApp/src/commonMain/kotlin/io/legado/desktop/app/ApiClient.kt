package io.legado.desktop.app

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 后端 HTTP 客户端（expect；desktopMain 用 java.net.http 实现）。
 * 契约见 docs/API.md：REST JSON，baseUrl 默认 http://127.0.0.1:2323。
 */
expect class ApiClient(baseUrl: String) {

    var baseUrl: String

    /** Web 书源访问令牌（x-legado-token，空则不携带）。默认关闭。 */
    var token: String

    /** GET 请求返回原始 JSON 文本（或抛异常） */
    suspend fun get(path: String): String

    /** GET 请求返回原始字节（封面/图片 /cover、/image 用），自动带 token 头 */
    suspend fun getBytes(path: String): ByteArray

    /** JSON body POST（写路由），自动带 token 头，返回原始 JSON 文本 */
    suspend fun postJson(path: String, body: String): String

    /** text/plain body POST（/saveJsSource 专用），自动带 token 头 */
    suspend fun postText(path: String, body: String): String

    /** multipart/form-data POST（上传本地书籍：fileName 参数 + fileData 文件） */
    suspend fun postMultipart(path: String, fileName: String, bytes: ByteArray): String
}

/** 搜索客户端（expect；desktopMain 用 JDK java.net.http.WebSocket 实现） */
expect class SearchClient(baseUrl: String, token: String) {

    /** WS 多源搜索：连上后发 {"key":..}；每个结果帧回调 onResult(原始 JSON)；结束/关闭回调 onDone */
    suspend fun search(key: String, onResult: (String) -> Unit, onDone: () -> Unit)
}

/** 本地文件选择结果 */
data class UploadedFile(val fileName: String, val bytes: ByteArray)

/** 弹出系统文件选择框选取本地书籍（TXT/EPUB/MOBI/UMD） */
expect suspend fun pickLocalBookFile(): UploadedFile?

// ---------------- 平台能力（desktopMain 实现，commonMain 无 JVM API） ----------------

/** 保存前端本地设置（多后端记忆/令牌，持久化到用户目录），返回是否成功 */
expect fun saveSettings(map: Map<String, String>): Boolean

/** 读取前端本地设置（无则空 Map） */
expect fun loadSettings(): Map<String, String>

/** 用系统默认浏览器打开 URL（网页登录过渡方案：系统浏览器登录 → 手动回填 Cookie） */
expect fun openInBrowser(url: String)

/** 解码图片字节为 Compose ImageBitmap（失败返回 null） */
expect fun decodeImage(bytes: ByteArray): ImageBitmap?

/** 直接抓取外部 URL 字节（正文图片/外部封面，不经后端） */
expect suspend fun fetchUrlBytes(url: String): ByteArray?
