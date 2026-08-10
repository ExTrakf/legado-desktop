package io.legado.desktop.utils

import io.legado.desktop.env.DesktopEnv

/**
 * 桌面版偏好读写（替代 Android SharedPreferences 扩展）。
 * 原调用形式 DesktopEnv.getPrefXxx(...) 在桌面版统一为顶层函数 getPrefXxx(...)。
 */

fun getPrefString(key: String, def: String = ""): String = DesktopEnv.getPrefString(key, def)

fun getPrefInt(key: String, def: Int = 0): Int = DesktopEnv.getPrefInt(key, def)

fun getPrefLong(key: String, def: Long = 0L): Long = DesktopEnv.getPrefLong(key, def)

fun getPrefBoolean(key: String, def: Boolean = false): Boolean = DesktopEnv.getPrefBoolean(key, def)

fun putPrefString(key: String, value: String?) = DesktopEnv.putPrefString(key, value)

fun putPrefInt(key: String, value: Int) = DesktopEnv.putPrefInt(key, value)

fun putPrefLong(key: String, value: Long) = DesktopEnv.putPrefLong(key, value)

fun putPrefBoolean(key: String, value: Boolean) = DesktopEnv.putPrefBoolean(key, value)

fun removePref(key: String) = DesktopEnv.removePref(key)
