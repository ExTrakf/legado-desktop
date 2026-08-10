@file:Suppress("unused")

package io.legado.desktop.help.book

import io.legado.desktop.data.entities.BookChapter
import io.legado.desktop.help.RuleBigDataHelp.getDanmakuFile

fun BookChapter.getDanmaku(): Any? { //读取弹幕数据
    return variableMap["danmaku"] ?: getDanmakuFile(bookUrl, url)
}