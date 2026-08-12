package io.legado.desktop.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow

/**
 * 远程图片（封面/正文图）。
 * path 为空 → 占位；以 http(s) 开头 → 外部直抓（fetchUrlBytes）；
 * 否则视为后端相对路径（/cover?path=...、/image?...）走 ApiClient.getBytes。
 */
@Composable
fun RemoteImage(
    state: AppState,
    path: String?,
    modifier: Modifier = Modifier,
    placeholder: String = "无封面",
) {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(path) { mutableStateOf(false) }
    LaunchedEffect(path) {
        bitmap = null
        failed = false
        if (path.isNullOrBlank()) return@LaunchedEffect
        val bytes = try {
            if (path.startsWith("http", ignoreCase = true)) fetchUrlBytes(path)
            else state.api.getBytes(path)
        } catch (_: Exception) {
            null
        }
        if (bytes == null) failed = true else bitmap = decodeImage(bytes)
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (failed) "加载失败" else placeholder,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** 正文渲染片段：纯文本 或 图片 URL */
sealed class ContentSeg {
    data class Text(val text: String) : ContentSeg()
    data class Img(val url: String) : ContentSeg()
}

/**
 * 从正文抽取图片（<img src> 与 markdown ![alt](url)），把正文切成 [文本, 图片, 文本, ...]。
 * 相对图片 URL 尝试用书源 origin 补全绝对地址（网页源正文图通常给绝对 URL）。
 */
fun parseContentImages(content: String): List<ContentSeg> {
    if (content.isBlank()) return listOf(ContentSeg.Text(""))
    val re = Regex(
        """<img[^>]*?src\s*=\s*["']([^"']+)["'][^>]*>|!\[[^\]]*\]\(([^)\s]+)\)""",
        RegexOption.IGNORE_CASE
    )
    val segments = mutableListOf<ContentSeg>()
    var pos = 0
    for (m in re.findAll(content)) {
        if (m.range.first > pos) {
            segments.add(ContentSeg.Text(content.substring(pos, m.range.first)))
        }
        val html = m.groupValues[1]
        val md = m.groupValues[2]
        segments.add(ContentSeg.Img(if (html.isNotEmpty()) html else md))
        pos = m.range.last + 1
    }
    if (pos < content.length) segments.add(ContentSeg.Text(content.substring(pos)))
    return segments
}

/** 解析为可直接抓取的 URL：绝对 http(s) 原样；相对路径用 origin 补全 */
fun resolveImageUrl(raw: String, origin: String?): String? {
    val t = raw.trim()
    if (t.isBlank()) return null
    if (t.startsWith("http://") || t.startsWith("https://")) return t
    val base = origin?.trim()?.trimEnd('/') ?: return null
    return base + "/" + t.trimStart('/')
}
