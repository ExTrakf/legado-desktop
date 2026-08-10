package io.legado.desktop.data.dao

import io.legado.desktop.data.entities.Cookie

interface CookieDao {

    fun get(url: String): Cookie?

    fun getOkHttpCookies(): List<Cookie>

    fun insert(vararg cookie: Cookie)

    fun update(vararg cookie: Cookie)

    fun delete(url: String)

    fun deleteOkHttp()
}