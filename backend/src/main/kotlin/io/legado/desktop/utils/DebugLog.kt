package io.legado.desktop.utils

/** 桌面版调试日志（替代 android 版 DebugLog） */
object DebugLog {
    fun log(msg: String) {
        LogUtils.d("Debug", msg)
    }

    fun e(tag: String, msg: String) {
        LogUtils.e(tag, msg)
    }

    fun e(tag: String, t: Throwable) {
        LogUtils.e(tag, t.stackTraceToString())
    }

    fun w(tag: String, msg: String) {
        LogUtils.e(tag, msg)
    }
}
