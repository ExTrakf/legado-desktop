package io.legado.desktop.constant

import io.legado.desktop.utils.LogUtils

/**
 * 应用内日志（内存环形列表，供前端/调试查询）。
 * 桌面版去掉了 Android Log/Toast，日志走 LogUtils(java.util.logging) + 内存列表。
 */
object AppLog {

    private val mLogs = arrayListOf<Triple<Long, String, Throwable?>>()

    val logs
        @Synchronized get() = mLogs.toList()

    @Synchronized
    fun put(message: String?, throwable: Throwable? = null, toast: Boolean = false) {
        message ?: return
        if (mLogs.size >= 100) {
            mLogs.removeLastOrNull()
        }
        if (throwable == null) {
            LogUtils.d("AppLog", message)
        } else {
            LogUtils.d("AppLog", "$message\n${throwable.stackTraceToString()}")
        }
        mLogs.add(0, Triple(System.currentTimeMillis(), message, throwable))
    }

    @Synchronized
    fun putNotSave(message: String?, throwable: Throwable? = null) {
        message ?: return
        if (mLogs.size >= 100) {
            mLogs.removeLastOrNull()
        }
        if (throwable == null) {
            LogUtils.e("AppLog", message)
        } else {
            LogUtils.e("AppLog", "$message\n${throwable.stackTraceToString()}")
        }
        mLogs.add(0, Triple(System.currentTimeMillis(), message, throwable))
    }

    @Synchronized
    fun putDebug(message: String?, throwable: Throwable? = null) {
        put(message, throwable)
    }

    @Synchronized
    fun clear() {
        mLogs.clear()
    }
}
