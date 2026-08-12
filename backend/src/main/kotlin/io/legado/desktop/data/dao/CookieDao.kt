package io.legado.desktop.data.dao

import io.legado.desktop.data.entities.Cookie

interface CookieDao {

    fun get(url: String): Cookie?

    fun getOkHttpCookies(): List<Cookie>

    /** 桌面新增：全部持久化 Cookie（Cookie 管理 API 用，原版无此查询） */
    fun all(): List<Cookie>

    /** 桌面新增：清空全部持久化 Cookie（Cookie 管理 API "清空全部" 用） */
    fun deleteAll()

    fun insert(vararg cookie: Cookie)

    fun update(vararg cookie: Cookie)

    fun delete(url: String)

    fun deleteOkHttp()
}