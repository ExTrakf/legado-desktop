package io.legado.desktop.model

import com.script.rhino.runScriptWithContext
import io.legado.desktop.constant.AppLog
import io.legado.desktop.data.entities.Book
import io.legado.desktop.data.entities.BookChapter
import io.legado.desktop.data.entities.BookSource
import io.legado.desktop.help.coroutine.Coroutine
import io.legado.desktop.utils.isTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

object SourceCallBack {
    const val CLICK_AUTHOR = "clickAuthor"
    const val LONG_CLICK_AUTHOR = "longClickAuthor"
    const val CLICK_BOOK_NAME = "clickBookName"
    const val LONG_CLICK_BOOK_NAME = "longClickBookName"
    const val CLICK_CUSTOM_BUTTON = "clickCustomButton"
    const val LONG_CLICK_CUSTOM_BUTTON = "longClickCustomButton"
    const val CLICK_SHARE_BOOK = "clickShareBook"
    const val CLICK_CLEAR_CACHE = "clickClearCache"
    const val CLICK_COPY_BOOK_URL = "clickCopyBookUrl"
    const val CLICK_COPY_TOC_URL = "clickCopyTocUrl"
    const val CLICK_COPY_PLAY_URL = "clickCopyPlayUrl"
    const val CLICK_BOOK_LABEL = "clickBookLabel"
    const val LONG_CLICK_BOOK_LABEL = "longClickBookLabel"

    const val ADD_BOOK_SHELF = "addBookShelf"
    const val DEL_BOOK_SHELF = "delBookShelf"
    const val SAVE_READ = "saveRead"
    const val START_READ = "startRead"
    const val END_READ = "endRead"
    const val START_SHELF_REFRESH = "startShelfRefresh"
    const val END_SHELF_REFRESH = "endShelfRefresh"
    fun callBackBtn(
        event: String,
        source: BookSource?,
        book: Book,
        chapter: BookChapter?,
        bookType: Int = 0,
        result: String? = null,
        noCall: (() -> Unit)? = null
    ) {
        // 桌面版无 UI 按钮事件，直接走 noCall
        noCall?.invoke()
    }

    fun callBackBook(
        event: String,
        source: BookSource?,
        book: Book?,
        chapter: BookChapter? = null,
        result: String? = null
    ) {
        Coroutine.async {
            callBackBookInternal(event, source, book, chapter, result)
        }
    }

    fun callBackBooks(
        event: String,
        books: List<Pair<BookSource?, Book>>,
    ) {
        if (books.isEmpty()) return
        Coroutine.async {
            books.forEach { (source, book) ->
                callBackBookInternal(event, source, book)
            }
        }
    }

    private suspend fun callBackBookInternal(
        event: String,
        source: BookSource?,
        book: Book?,
        chapter: BookChapter? = null,
        result: String? = null,
    ) {
        if (source == null || book == null || !source.eventListener) return
        val jsStr = source.getContentRule().callBackJs
        if (jsStr.isNullOrEmpty()) return
        kotlin.runCatching {
            withTimeout(60000L) {
                runScriptWithContext(kotlin.coroutines.coroutineContext) {
                    source.evalJS(jsStr) {
                        put("event", event)
                        put("result", result)
                        put("book", book)
                        put("chapter", chapter)
                    }
                }
            }
        }.onFailure {
            AppLog.put("${source.bookSourceName}\n书源执行回调事件${event}出错\n${it.localizedMessage}", it, true)
        }
    }

    fun callBackSource(scope: CoroutineScope, event: String, source: BookSource) {
        val jsStr = source.getContentRule().callBackJs
        if (jsStr.isNullOrEmpty()) return
        scope.launch(IO) {
            kotlin.runCatching {
                withTimeout(30000L) {
                    runScriptWithContext {
                        source.evalJS(jsStr) {
                            put("event", event)
                            put("result", null)
                            put("book", null)
                            put("chapter", null)
                        }
                    }
                }
            }.onFailure {
                AppLog.put("${source.bookSourceName}\n书源执行回调事件${event}出错\n${it.localizedMessage}", it, true)
            }
        }
    }

}
