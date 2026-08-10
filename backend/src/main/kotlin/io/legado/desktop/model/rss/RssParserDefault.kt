package io.legado.desktop.model.rss

import io.legado.desktop.data.entities.RssArticle
import io.legado.desktop.model.Debug
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.IOException
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 桌面版 RSS 默认解析（原 XmlPullParser → javax.xml DOM，解析逻辑等价）。
 */
@Suppress("unused")
object RssParserDefault {

    @Throws(IOException::class)
    fun parseXML(
        sortName: String,
        xml: String,
        sourceUrl: String
    ): Pair<MutableList<RssArticle>, String?> {

        val articleList = mutableListOf<RssArticle>()

        val builder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
        val doc = builder.parse(ByteArrayInputStream(xml.toByteArray()))

        // 收集所有 <item> 元素
        val itemElements = arrayListOf<Element>()
        val all = doc.getElementsByTagName("*")
        for (i in 0 until all.length) {
            val node = all.item(i)
            if (node is Element && node.localName.equals(RSS_ITEM, true)) {
                itemElements.add(node)
            }
        }

        for (item in itemElements) {
            val article = RssArticle()

            childText(item, RSS_ITEM_TITLE)?.let { article.title = it.trim() }
            childText(item, RSS_ITEM_LINK)?.let { article.link = it.trim() }

            // thumbnail: <media:thumbnail url="..."/>
            childElements(item, RSS_ITEM_THUMBNAIL).firstOrNull()?.let {
                article.image = it.getAttribute(RSS_ITEM_URL).takeIf { a -> a.isNotBlank() }
            }

            // enclosure: <enclosure type="image/*" url="..."/>
            childElements(item, RSS_ITEM_ENCLOSURE).firstOrNull()?.let {
                val type = it.getAttribute(RSS_ITEM_TYPE)
                if (type.contains("image/")) {
                    article.image = it.getAttribute(RSS_ITEM_URL).takeIf { a -> a.isNotBlank() }
                }
            }

            // description
            childText(item, RSS_ITEM_DESCRIPTION)?.let {
                article.description = it.trim()
                if (article.image == null) {
                    article.image = getImageUrl(it)
                }
            }

            // content:encoded
            childText(item, RSS_ITEM_CONTENT)?.let {
                article.content = it.trim()
                if (article.image == null) {
                    article.image = getImageUrl(it)
                }
            }

            // pubDate / time
            article.pubDate = childText(item, RSS_ITEM_PUB_DATE)?.trim()
                ?: childText(item, RSS_ITEM_TIME)?.trim() ?: ""

            article.origin = sourceUrl
            article.sort = sortName
            articleList.add(article)
        }

        articleList.firstOrNull()?.let {
            Debug.log(sourceUrl, "┌获取标题")
            Debug.log(sourceUrl, "└${it.title}")
            Debug.log(sourceUrl, "┌获取时间")
            Debug.log(sourceUrl, "└${it.pubDate}")
            Debug.log(sourceUrl, "┌获取描述")
            Debug.log(sourceUrl, "└${it.description}")
            Debug.log(sourceUrl, "┌获取图片url")
            Debug.log(sourceUrl, "└${it.image}")
            Debug.log(sourceUrl, "┌获取文章链接")
            Debug.log(sourceUrl, "└${it.link}")
        }
        return Pair(articleList, null)
    }

    /** 取子元素文本（按 localName 匹配，兼容 content:encoded 等带前缀标签） */
    private fun childText(parent: Element, name: String): String? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.localName.equals(name, true)) {
                return node.textContent?.trim()
            }
        }
        return null
    }

    /** 取子元素列表（按 localName 匹配） */
    private fun childElements(parent: Element, name: String): List<Element> {
        val result = arrayListOf<Element>()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.localName.equals(name, true)) {
                result.add(node)
            }
        }
        return result
    }

    /**
     * Finds the first img tag and get the src as featured image
     *
     * @param input The content in which to search for the tag
     * @return The url, if there is one
     */
    private fun getImageUrl(input: String): String? {

        var url: String? = null
        val patternImg = "(<img [^>]*>)".toPattern()
        val matcherImg = patternImg.matcher(input)
        if (matcherImg.find()) {
            val imgTag = matcherImg.group(1)
            val patternLink = "src\\s*=\\s*\"([^\"]+)\"".toPattern()
            val matcherLink = patternLink.matcher(imgTag!!)
            if (matcherLink.find()) {
                url = matcherLink.group(1)!!.trim()
            }
        }
        return url
    }

    private const val RSS_ITEM = "item"
    private const val RSS_ITEM_TITLE = "title"
    private const val RSS_ITEM_LINK = "link"
    private const val RSS_ITEM_CATEGORY = "category"
    private const val RSS_ITEM_THUMBNAIL = "media:thumbnail"
    private const val RSS_ITEM_ENCLOSURE = "enclosure"
    private const val RSS_ITEM_DESCRIPTION = "description"
    private const val RSS_ITEM_CONTENT = "content:encoded"
    private const val RSS_ITEM_PUB_DATE = "pubDate"
    private const val RSS_ITEM_TIME = "time"
    private const val RSS_ITEM_URL = "url"
    private const val RSS_ITEM_TYPE = "type"

}
