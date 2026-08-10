package io.legado.desktop.help.source

import com.script.rhino.runScriptWithContext
import io.legado.desktop.data.entities.RssSource
import io.legado.desktop.exception.NoStackTraceException
import io.legado.desktop.help.SimpleACache
import io.legado.desktop.utils.MD5Utils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// 桌面版：rssSortUrl 缓存走 SimpleACache

internal fun RssSource.requireSourceUrl() {
    if (sourceUrl.isNullOrBlank()) {
        throw NoStackTraceException("不是订阅源")
    }
}

private fun RssSource.getSortUrlsKey(): String {
    return MD5Utils.md5Encode(sourceUrl + sortUrl)
}

private const val jsPrefix = "@js:"
private const val jsTagPrefix = "<js>"
private const val jsTagSuffix = "</js>"
private val sortSeparator = Regex("(?:&&|\\r?\\n)+")

private fun String?.isRssSortScript(): Boolean {
    return this?.startsWith(jsPrefix) == true || this?.startsWith(jsTagPrefix) == true
}

internal fun extractRssSortScript(sortUrl: String): String? {
    return when {
        sortUrl.startsWith(jsPrefix) -> sortUrl.substring(jsPrefix.length).takeIf { it.isNotBlank() }
        sortUrl.startsWith(jsTagPrefix) -> {
            val closeIndex = sortUrl.lastIndexOf(jsTagSuffix)
            if (closeIndex < jsTagPrefix.length) {
                null
            } else {
                sortUrl.substring(jsTagPrefix.length, closeIndex).takeIf { it.isNotBlank() }
            }
        }

        else -> null
    }
}

internal fun parseRssSortUrls(value: String?, sourceUrl: String): List<Pair<String, String>> {
    val entries = value.orEmpty()
        .split(sortSeparator)
        .mapNotNull { item ->
            val url = item.substringAfter("::", "").trim()
            if (url.isEmpty()) {
                null
            } else {
                item.substringBefore("::").trim() to url
            }
        }
    return entries.ifEmpty { listOf("" to sourceUrl) }
}

internal suspend fun resolveRssSortUrls(
    configuredSortUrl: String?,
    sourceUrl: String,
    cachedScriptResult: String? = null,
    evaluateScript: suspend (String) -> String? = { null },
): List<Pair<String, String>> {
    val resolvedValue = try {
        if (configuredSortUrl.isRssSortScript()) {
            extractRssSortScript(configuredSortUrl.orEmpty())?.let { script ->
                cachedScriptResult?.takeIf { it.isNotBlank() } ?: evaluateScript(script)
            }
        } else {
            configuredSortUrl
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
    return parseRssSortUrls(resolvedValue, sourceUrl)
}

suspend fun RssSource.sortUrls(): List<Pair<String, String>> {
    val configuredSortUrl = sortUrl
    val sortUrlsKey = getSortUrlsKey()
    return withContext(Dispatchers.IO) {
        val cachedResult = if (configuredSortUrl.isRssSortScript()) {
            runCatching { SimpleACache.getAsString(sortUrlsKey) }.getOrNull()
        } else {
            null
        }
        var evaluatedResult: String? = null
        resolveRssSortUrls(configuredSortUrl, sourceUrl, cachedResult) { script ->
            runScriptWithContext {
                evalJS(script)?.toString()
            }.also { evaluatedResult = it }
        }.also {
            if (cachedResult.isNullOrBlank()) {
                evaluatedResult?.takeIf { result -> result.isNotBlank() }?.let { result ->
                    runCatching { SimpleACache.put(sortUrlsKey, result) }
                }
            }
        }
    }
}

suspend fun RssSource.removeSortCache() {
    withContext(Dispatchers.IO) {
        SimpleACache.remove(getSortUrlsKey())
    }
}
