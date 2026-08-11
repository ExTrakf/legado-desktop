package io.legado.desktop.help.webView

/**
 * 池化 WebView 包装（对应原版 help/webView/PooledWebView.kt，桌面等价）。
 * 原版 upContext（MutableContextWrapper 换 baseContext）为 Android View 概念，桌面版不需要。
 */
class PooledWebView(
    val realWebView: DesktopWebView, // 真正的浏览器实例（JCEF 实现）
    val id: String // 唯一标识
) {
    var isInUse: Boolean = false // 是否正在被使用
    var lastUseTime: Long = 0 // 最后一次被使用的时间戳
}
