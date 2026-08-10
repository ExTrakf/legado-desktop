package io.legado.desktop.data.entities

import io.legado.desktop.constant.AppLog
import io.legado.desktop.constant.AppPattern
import io.legado.desktop.data.appDb
import io.legado.desktop.exception.RegexTimeoutException
import io.legado.desktop.help.RuleBigDataHelp
import io.legado.desktop.help.config.AppConfig
import io.legado.desktop.model.analyzeRule.AnalyzeUrl
import io.legado.desktop.model.analyzeRule.RuleDataInterface
import io.legado.desktop.utils.ChineseUtils
import io.legado.desktop.utils.GSON
import io.legado.desktop.utils.LogUtils
import io.legado.desktop.utils.MD5Utils
import io.legado.desktop.utils.NetworkUtils
import io.legado.desktop.utils.fromJsonObject
import io.legado.desktop.utils.replace
import kotlinx.coroutines.CancellationException

data class BookChapter(
    var url: String = "",               // 章节地址
    var title: String = "",             // 章节标题
    var isVolume: Boolean = false,      // 是否是卷名
    var baseUrl: String = "",           // 用来拼接相对url
    var bookUrl: String = "",           // 书籍地址
    var index: Int = 0,                 // 章节序号
    var isVip: Boolean = false,         // 是否VIP
    var isPay: Boolean = false,         // 是否已购买
    var resourceUrl: String? = null,    // 音频真实URL
    var tag: String? = null,            // 更新时间或其他章节附加信息
    var wordCount: String? = null,      // 本章节字数
    var start: Long? = null,            // 章节起始位置
    var end: Long? = null,              // 章节终止位置
    var startFragmentId: String? = null,  //EPUB书籍当前章节的fragmentId
    var endFragmentId: String? = null,    //EPUB书籍下一章节的fragmentId
    var variable: String? = null,        //变量
    var imgUrl: String? = null // 标题段评图或者视频封面
) : RuleDataInterface {

    @delegate:Transient
    override val variableMap: HashMap<String, String> by lazy {
        GSON.fromJsonObject<HashMap<String, String>>(variable).getOrNull() ?: hashMapOf()
    }

    fun putImgUrl(value: String?) {
        imgUrl =value
        update()
    }

    fun putLyric(value: String?) { //存入歌词文本
        if (super.putVariable("lyric", value)) {
            variable = GSON.toJson(variableMap)
            update()
        }
    }

    fun putDanmaku(value: String?) { //存入弹幕文本
        if (super.putVariable("danmaku", value)) {
            variable = GSON.toJson(variableMap)
            update()
        }
    }

    fun update() {
        appDb.bookChapterDao.update(this)
    }

    var titleMD5: String? = null

    override fun putVariable(key: String, value: String?): Boolean {
        if (super.putVariable(key, value)) {
            variable = GSON.toJson(variableMap)
        }
        return true
    }

    override fun putBigVariable(key: String, value: String?) {
        RuleBigDataHelp.putChapterVariable(bookUrl, url, key, value)
    }

    override fun getBigVariable(key: String): String? {
        return RuleBigDataHelp.getChapterVariable(bookUrl, url, key)
    }

    override fun hashCode() = url.hashCode()

    override fun equals(other: Any?): Boolean {
        if (other is BookChapter) {
            return other.url == url
        }
        return false
    }

    fun primaryStr(): String {
        return bookUrl + url
    }

    fun getDisplayTitle(
        replaceRules: List<ReplaceRule>? = null,
        useReplace: Boolean = true,
        chineseConvert: Boolean = true,
        replaceBook: ReplaceBook? = null
    ): String {
        var displayTitle = title.replace(AppPattern.rnRegex, "")
        if (chineseConvert) {
            when (AppConfig.chineseConverterType) {
                1 -> displayTitle = ChineseUtils.t2s(displayTitle)
                2 -> displayTitle = ChineseUtils.s2t(displayTitle)
            }
        }
        if (useReplace && replaceRules != null) kotlin.run {
            replaceRules.forEach { item ->
                if (item.pattern.isNotEmpty()) {
                    try {
                        val mDisplayTitle = if (item.isRegex) {
                            displayTitle.replace(
                                item.name,
                                item.regex,
                                item.replacement,
                                item.getValidTimeoutMillisecond(),
                                this@BookChapter,
                                replaceBook
                            )
                        } else {
                            displayTitle.replace(item.pattern, item.replacement)
                        }
                        if (mDisplayTitle.isNotBlank()) {
                            displayTitle = mDisplayTitle
                        }
                    } catch (_: RegexTimeoutException) {
                        item.isEnabled = false
                        appDb.replaceRuleDao.update(item)
                    } catch (_: CancellationException) {
                        return@run
                    } catch (e: Exception) {
                        AppLog.put("${item.name}替换出错\n替换内容\n${displayTitle}", e)
                        LogUtils.e("替换规则", "${item.name}替换出错\n${e.stackTraceToString()}")
                    }
                }
            }
        }
        return displayTitle
    }

    fun getAbsoluteURL(): String {
        //二级目录解析的卷链接为空 返回目录页的链接
        if (url.startsWith(title) && isVolume) return baseUrl
        val urlMatcher = AnalyzeUrl.paramPattern.matcher(url)
        val urlBefore = if (urlMatcher.find()) url.substring(0, urlMatcher.start()) else url
        val urlAbsoluteBefore = NetworkUtils.getAbsoluteURL(baseUrl, urlBefore)
        return if (urlBefore.length == url.length) {
            urlAbsoluteBefore
        } else {
            "$urlAbsoluteBefore," + url.substring(urlMatcher.end())
        }
    }

    private fun ensureTitleMD5Init() {
        if (titleMD5 == null) {
            titleMD5 = MD5Utils.md5Encode16(title)
        }
    }

    @Suppress("DefaultLocale", "unused")
    fun getFileName(suffix: String = "nb"): String {
        ensureTitleMD5Init()
        return String.format("%05d-%s.%s", index, titleMD5, suffix)
    }

    @Suppress("DefaultLocale", "unused")
    fun getFontName(): String {
        ensureTitleMD5Init()
        return String.format("%05d-%s.ttf", index, titleMD5)
    }
}

