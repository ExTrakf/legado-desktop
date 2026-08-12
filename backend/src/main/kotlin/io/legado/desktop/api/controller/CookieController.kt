package io.legado.desktop.api.controller

import com.google.gson.JsonObject
import io.legado.desktop.api.ReturnData
import io.legado.desktop.data.appDb
import io.legado.desktop.help.http.CookieStore
import io.legado.desktop.utils.GSON

/**
 * Cookie 管理 API（桌面新增，支撑前端网页登录过渡方案：系统浏览器登录 → 手动回填 Cookie）。
 * 契约：GET /getCookies（令牌保护）｜POST /setCookie（{"url","cookie"}）｜POST /clearCookies（{"url"}，url 空=清空全部）。
 */
object CookieController {

    /** 全部持久化 Cookie 列表 */
    val cookies: ReturnData
        get() = ReturnData().setData(appDb.cookieDao.all())

    /** 写入/更新 Cookie：cookie 用完整 "k=v; k2=v2" 串，按原版 CookieStore.replaceCookie 语义合并 */
    fun setCookie(postData: String?): ReturnData {
        val returnData = ReturnData()
        val json = runCatching { GSON.fromJson(postData, JsonObject::class.java) }.getOrNull()
            ?: return returnData.setErrorMsg("参数错误")
        val url = json.get("url")?.asString?.trim().orEmpty()
        val cookie = json.get("cookie")?.asString.orEmpty()
        if (url.isBlank()) {
            return returnData.setErrorMsg("url不能为空")
        }
        CookieStore.replaceCookie(url, cookie)
        return returnData.setData("")
    }

    /** 清除 Cookie：url 为空 = 清空全部（CookieStore.clear 对齐原版 CookieManager 清空） */
    fun clearCookies(postData: String?): ReturnData {
        val returnData = ReturnData()
        val url = runCatching { GSON.fromJson(postData, JsonObject::class.java) }
            .getOrNull()
            ?.get("url")
            ?.asString
            ?.trim()
        if (url.isNullOrBlank()) {
            appDb.cookieDao.deleteAll()
        } else {
            CookieStore.removeCookie(url)
        }
        return returnData.setData("")
    }
}
