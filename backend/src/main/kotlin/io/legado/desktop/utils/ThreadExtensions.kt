package io.legado.desktop.utils

/** 桌面版无 Android 主线程概念：JS/引擎代码始终视为后台线程执行 */
val isMainThread: Boolean get() = false
