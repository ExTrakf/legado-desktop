package io.legado.desktop.model

import io.legado.desktop.constant.AppLog
import io.legado.desktop.env.DesktopEnv
import io.legado.desktop.help.coroutine.Coroutine
import io.legado.desktop.model.analyzeRule.AnalyzeUrl
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * 文件下载（对应原版 model/Download.kt + service/DownloadService.kt，桌面等价）。
 * 原版用 Android DownloadManager 下载到系统 Downloads 目录并弹通知；
 * 桌面版用 AnalyzeUrl（OkHttp）流式下载到 <数据目录>/cache/downloads，无通知。
 */
object Download {

    fun start(url: String, fileName: String) {
        val safeName = fileName.substringAfterLast('/').substringAfterLast('\\')
        Coroutine.async(context = Dispatchers.IO) {
            try {
                val dir = File(DesktopEnv.cacheDir.toFile(), "downloads").apply { mkdirs() }
                val file = File(dir, safeName)
                AnalyzeUrl(url).getInputStreamAwait().use { input ->
                    file.outputStream().buffered().use { out -> input.copyTo(out) }
                }
                AppLog.put("下载完成 ${file.absolutePath}")
            } catch (e: Exception) {
                AppLog.put("下载失败 $url\n${e.localizedMessage}", e)
            }
        }
    }
}
