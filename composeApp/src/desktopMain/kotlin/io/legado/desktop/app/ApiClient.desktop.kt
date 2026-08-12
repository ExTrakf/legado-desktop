package io.legado.desktop.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

actual class ApiClient actual constructor(baseUrl: String) {

    actual var baseUrl: String = baseUrl

    actual var token: String = ""

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private fun HttpRequest.Builder.applyToken(): HttpRequest.Builder {
        if (token.isNotBlank()) {
            header("x-legado-token", token)
        }
        return this
    }

    actual suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .GET()
            .timeout(Duration.ofSeconds(30))
            .applyToken()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw RuntimeException("HTTP ${response.statusCode()}: ${response.body()}")
        }
        response.body()
    }

    actual suspend fun postJson(path: String, body: String): String =
        withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(60))
                .applyToken()
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                throw RuntimeException("HTTP ${response.statusCode()}: ${response.body()}")
            }
            response.body()
        }

    actual suspend fun postText(path: String, body: String): String =
        withContext(Dispatchers.IO) {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "text/plain; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(60))
                .applyToken()
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                throw RuntimeException("HTTP ${response.statusCode()}: ${response.body()}")
            }
            response.body()
        }

    actual suspend fun postMultipart(path: String, fileName: String, bytes: ByteArray): String =
        withContext(Dispatchers.IO) {
            val boundary = "----legado${System.currentTimeMillis()}"
            // fileName 也作为 form 字段 + fileData 文件部分（NanoHTTPD 需要文件部分带 filename）
            val safeName = fileName.replace("\"", "").replace("\r", "").replace("\n", "")
            val parts = listOf(
                "--$boundary\r\n".toByteArray(),
                "Content-Disposition: form-data; name=\"fileName\"\r\n\r\n".toByteArray(),
                safeName.toByteArray(StandardCharsets.UTF_8),
                "\r\n--$boundary\r\n".toByteArray(),
                "Content-Disposition: form-data; name=\"fileData\"; filename=\"$safeName\"\r\n".toByteArray(),
                "Content-Type: application/octet-stream\r\n\r\n".toByteArray(),
                bytes,
                "\r\n--$boundary--\r\n".toByteArray(),
            )
            val body = ByteArrayOutputStream().use { out ->
                parts.forEach { out.write(it) }
                out.toByteArray()
            }
            val request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "multipart/form-data; boundary=$boundary")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .timeout(Duration.ofSeconds(120))
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                throw RuntimeException("HTTP ${response.statusCode()}: ${response.body()}")
            }
            response.body()
        }
}

actual suspend fun pickLocalBookFile(): UploadedFile? = withContext(Dispatchers.Main) {
    val dialog = FileDialog(null as java.awt.Frame?, "选择本地书籍（TXT/EPUB/MOBI/UMD）", FileDialog.LOAD)
    dialog.isMultipleMode = false
    dialog.isVisible = true // 原生模态对话框（需在 AWT EDT 线程）
    val file = dialog.files.firstOrNull()
    dialog.dispose()
    file?.let { UploadedFile(it.name, it.readBytes()) }
}
