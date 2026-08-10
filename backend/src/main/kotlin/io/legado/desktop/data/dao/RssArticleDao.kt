package io.legado.desktop.data.dao

import io.legado.desktop.data.entities.RssArticle
import kotlinx.coroutines.flow.Flow

interface RssArticleDao {

    fun get(origin: String, link: String, sort: String): RssArticle?

    fun getByLink(origin: String, link: String): RssArticle?

    
    fun flowByOriginSort(origin: String, sort: String): Flow<List<RssArticle>>

    fun insert(vararg rssArticle: RssArticle)

    fun append(vararg rssArticle: RssArticle)

    fun clearOld(origin: String, sort: String, order: Long)

    fun update(vararg rssArticle: RssArticle)

    fun updateOrigin(origin: String, oldOrigin: String)

    fun delete(origin: String)

}