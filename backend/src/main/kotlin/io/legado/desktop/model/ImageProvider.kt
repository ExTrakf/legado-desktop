package io.legado.desktop.model

import io.legado.desktop.constant.AppLog.putDebug
import io.legado.desktop.data.entities.Book
import io.legado.desktop.data.entities.BookSource
import io.legado.desktop.help.book.BookHelp
import io.legado.desktop.help.book.isEpub
import io.legado.desktop.help.book.isMobi
import io.legado.desktop.model.localBook.EpubFile
import io.legado.desktop.model.localBook.MobiFile
import io.legado.desktop.utils.BitmapUtils
import io.legado.desktop.utils.FileUtils
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * 正文图片提供（原版 Bitmap/LruCache → 桌面返回图片字节）。
 * 缓存/下载逻辑与原版等价。
 */
object ImageProvider {

    /**
     *缓存网络图片和epub/mobi图片
     */
    suspend fun cacheImage(
        book: Book,
        src: String,
        bookSource: BookSource?
    ): File {
        return withContext(IO) {
            val vFile = BookHelp.getImage(book, src)
            if (!BookHelp.isImageExist(book, src)) {
                val inputStream = when {
                    book.isEpub -> EpubFile.getImage(book, src)
                    book.isMobi -> MobiFile.getImage(book, src)
                    else -> {
                        BookHelp.saveImage(bookSource, book, src)
                        null
                    }
                }
                inputStream?.use { input ->
                    val newFile = FileUtils.createFileIfNotExist(vFile.absolutePath)
                    FileOutputStream(newFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            return@withContext vFile
        }
    }

    /**
     * 获取图片字节（按 width 缩放，width<=0 返回原始字节）；文件不存在返回 null
     */
    fun getImageBytes(
        book: Book,
        src: String,
        width: Int
    ): ByteArray? {
        val vFile = BookHelp.getImage(book, src)
        if (!vFile.exists()) return null
        val bytes = vFile.readBytes()
        if (bytes.isEmpty()) return null
        return if (width > 0) BitmapUtils.resize(bytes, width) else bytes
    }

}
