package io.legado.desktop.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

actual class ApiClient actual constructor(baseUrl: String) {

    actual var baseUrl: String = baseUrl

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    actual suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .GET()
            .timeout(Duration.ofSeconds(30))
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            throw RuntimeException("HTTP ${response.statusCode()}: ${response.body()}")
        }
        response.body()
    }
}
