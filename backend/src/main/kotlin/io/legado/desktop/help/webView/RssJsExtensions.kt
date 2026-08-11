package io.legado.desktop.help.webView

import io.legado.desktop.compat.JavascriptInterface
import io.legado.desktop.data.entities.BaseSource
import io.legado.desktop.help.JsExtensions
import io.legado.desktop.model.analyzeRule.AnalyzeRule
import java.lang.ref.WeakReference
import java.net.URL

/**
 * JS 扩展基类（对应原版 ui/rss/read/RssJsExtensions.kt 的纯逻辑部分，桌面裁剪 UI）。
 * 原版还包含 searchBook/addBook/showPhoto/open（跳转 UI）与 bookType 相关 book/chapter 绑定，
 * 桌面版无 Android UI 全部裁剪；analyzeRule 因无 ReadBook/AudioPlay/VideoPlay 全局状态，
 * 仅绑定 source（原版 bookType 默认 0 时 book/chapter 也为 null，语义等价）。
 */
@Suppress("unused")
open class RssJsExtensions(source: BaseSource?) : JsExtensions {

    val sourceRef: WeakReference<BaseSource?> = WeakReference(source)

    override fun getSource(): BaseSource? {
        return sourceRef.get()
    }

    override fun getTag(): String? {
        return getSource()?.getKey()
    }

    @JavascriptInterface
    fun put(key: String, value: String): String {
        getSource()?.put(key, value)
        return value
    }

    @JavascriptInterface
    fun get(key: String): String {
        return getSource()?.get(key) ?: ""
    }

    /** AnalyzeRule实现（对应原版，book/chapter 桌面版恒为空） **/
    val analyzeRule by lazy {
        AnalyzeRule(source = getSource())
    }

    @JavascriptInterface
    @JvmOverloads
    fun setContent(content: Any?, baseUrl: String? = null): AnalyzeRule {
        return analyzeRule.setContent(content, baseUrl)
    }

    @JavascriptInterface
    fun setBaseUrl(baseUrl: String?): AnalyzeRule {
        return analyzeRule.setBaseUrl(baseUrl)
    }

    @JavascriptInterface
    fun setRedirectUrl(url: String): URL? {
        return analyzeRule.setRedirectUrl(url)
    }

    @JvmOverloads
    fun getStringList(rule: String?, mContent: Any? = null, isUrl: Boolean = false): List<String>? {
        return analyzeRule.getStringList(rule, mContent, isUrl)
    }

    @JvmOverloads
    fun getString(ruleStr: String?, mContent: Any? = null, isUrl: Boolean = false): String {
        return analyzeRule.getString(ruleStr, mContent, isUrl)
    }

    @JavascriptInterface
    fun getString(ruleStr: String?, unescape: Boolean): String {
        return analyzeRule.getString(ruleStr, unescape)
    }

    fun getElement(ruleStr: String): Any? {
        return analyzeRule.getElement(ruleStr)
    }

    fun getElements(ruleStr: String): List<Any> {
        return analyzeRule.getElements(ruleStr)
    }

}
