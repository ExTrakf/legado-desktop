package io.legado.desktop.model.localBook

import io.legado.desktop.constant.AppLog
import io.legado.desktop.data.entities.Book
import io.legado.desktop.data.entities.BookChapter
import io.legado.desktop.exception.NoStackTraceException
import io.legado.desktop.utils.printOnDebug
import java.io.InputStream

/**
 * 桌面版 PdfFile：原版基于 Android PdfRenderer（渲染 PDF 页面为 Bitmap），
 * 桌面 JVM 无对应 API，暂不解析 PDF（本地书籍导入 PDF 时报错，其余格式不受影响）。
 */
class PdfFile(var book: Book) : AutoCloseable {
    companion object : BaseLocalBookParse {
        private val cache = CloseableCache<PdfFile>()

        /**
         * pdf分页尺寸
         */
        const val PAGE_SIZE = 10

        @Synchronized
        private fun getPFile(book: Book): PdfFile {
            return cache.getOrCreate(
                matches = { it.openedBookUrl == book.bookUrl },
                create = { PdfFile(book) },
            ).also { it.book = book }
        }

        @Synchronized
        override fun upBookInfo(book: Book) {
            getPFile(book).upBookInfo()
        }

        @Synchronized
        override fun getChapterList(book: Book): ArrayList<BookChapter> {
            return getPFile(book).getChapterList()
        }

        @Synchronized
        override fun getContent(book: Book, chapter: BookChapter): String? {
            return getPFile(book).getContent(chapter)
        }

        @Synchronized
        override fun getImage(book: Book, href: String): InputStream? {
            return getPFile(book).getImage(href)
        }

        @Synchronized
        fun clear() {
            cache.clear()
        }

        @Synchronized
        fun clear(bookUrl: String) {
            cache.clearIf { it.openedBookUrl == bookUrl }
        }

    }

    private val openedBookUrl = book.bookUrl

    init {
        upBookCover(true)
    }

    private fun unsupported(): Nothing = throw NoStackTraceException(
        "桌面版暂不支持 PDF 解析（原 Android PdfRenderer）"
    )

    private fun getContent(chapter: BookChapter): String? = unsupported()

    private fun getImage(href: String): InputStream? = unsupported()

    private fun getChapterList(): ArrayList<BookChapter> = unsupported()

    private fun upBookCover(fastCheck: Boolean = false) = Unit

    private fun upBookInfo() {
        try {
            unsupported()
        } catch (e: Exception) {
            AppLog.put("读取Pdf文件失败\n${e.localizedMessage}", e)
            e.printOnDebug()
        }
    }

    override fun close() {
        // 原 closePdf()
    }
}
