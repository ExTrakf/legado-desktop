package io.legado.desktop.model

import io.legado.desktop.exception.NoStackTraceException

object Download {

    fun start(context: Any, url: String, fileName: String) {
        // 桌面版：下载执行逻辑（原 DownloadService）在后续移植
        throw NoStackTraceException("桌面版下载服务尚未实现")
    }

}