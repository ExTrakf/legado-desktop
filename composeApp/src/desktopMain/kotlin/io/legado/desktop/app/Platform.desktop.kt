package io.legado.desktop.app

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.skia.Image
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * 平台能力 desktop actual（commonMain 无 JVM API，教训40）。
 *
 * - 本地设置持久化：`~/.legado-desktop-frontend.json`（多后端记忆 / 令牌）
 * - 系统浏览器打开登录页（T7.8 网页登录过渡方案）
 * - 图片解码（Skia）与外部 URL 抓取
 */
private val settingsFile: File
    get() = File(System.getProperty("user.home"), ".legado-desktop-frontend.json")

private val prefsJson = Json { ignoreUnknownKeys = true }

actual fun saveSettings(map: Map<String, String>): Boolean = runCatching {
    settingsFile.parentFile?.mkdirs()
    settingsFile.writeText(prefsJson.encodeToString(map), Charsets.UTF_8)
}.isSuccess

actual fun loadSettings(): Map<String, String> = runCatching {
    prefsJson.decodeFromString<Map<String, String>>(settingsFile.readText(Charsets.UTF_8))
}.getOrElse { emptyMap() }

actual fun openInBrowser(url: String) {
    if (!Desktop.isDesktopSupported()) return
    runCatching { Desktop.getDesktop().browse(URI.create(url)) }
}

actual fun decodeImage(bytes: ByteArray): ImageBitmap? = try {
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
} catch (_: Exception) {
    null
}

private val urlHttp: HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(10))
    .followRedirects(HttpClient.Redirect.NORMAL)
    .build()

actual suspend fun fetchUrlBytes(url: String): ByteArray? = withContext(Dispatchers.IO) {
    runCatching {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "legado-desktop/0.1.0")
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build()
        val response = urlHttp.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() in 200..299) response.body() else null
    }.getOrNull()
}
