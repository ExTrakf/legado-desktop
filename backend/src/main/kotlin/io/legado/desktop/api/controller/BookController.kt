package io.legado.desktop.api.controller

import io.legado.desktop.api.ReturnData
import io.legado.desktop.data.appDb
import io.legado.desktop.data.entities.Book
import io.legado.desktop.data.entities.BookProgress
import io.legado.desktop.help.book.BookHelp
import io.legado.desktop.help.book.ContentProcessor
import io.legado.desktop.help.book.isLocal
import io.legado.desktop.help.book.update
import io.legado.desktop.help.config.AppConfig
import io.legado.desktop.help.CacheManager
import io.legado.desktop.model.CacheBook
import io.legado.desktop.model.analyzeRule.AnalyzeUrl
import io.legado.desktop.model.ImageProvider
import io.legado.desktop.model.localBook.LocalBook
import io.legado.desktop.model.webBook.WebBook
import io.legado.desktop.utils.GSON
import io.legado.desktop.utils.cnCompare
import io.legado.desktop.utils.fromJsonObject
import io.legado.desktop.utils.isAbsUrl
import io.legado.desktop.utils.printOnDebug
import io.legado.desktop.utils.stackTraceStr
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File

private val windowsDrivePrefix = Regex("^[A-Za-z]:")

internal fun requireSafeUploadedBookFileName(fileName: String): String {
    val isUnsafe = fileName.isBlank() ||
            fileName == "." ||
            fileName == ".." ||
            fileName.indexOfAny(charArrayOf('/', '\\')) >= 0 ||
            windowsDrivePrefix.containsMatchIn(fileName) ||
            fileName.any { Character.isISOControl(it) }
    require(!isUnsafe) { "Invalid uploaded book file name" }
    return fileName
}

object BookController {

    /**
     * 全部书籍分组（桌面新增：GET /getBookGroups，前端书架分组显示用）
     */
    val bookGroups: ReturnData
        get() = ReturnData().setData(appDb.bookGroupDao.all)

    /**
     * 书架所有书籍
     */
    val bookshelf: ReturnData
        get() {
            val books = appDb.bookDao.all
            val returnData = ReturnData()
            return if (books.isEmpty()) {
                returnData.setErrorMsg("还没有添加小说")
            } else {
                val data = when (AppConfig.bookshelfSort) {
                    1 -> books.sortedByDescending { it.latestChapterTime }
                    2 -> books.sortedWith { o1, o2 ->
                        o1.name.cnCompare(o2.name)
                    }

                    3 -> books.sortedBy { it.order }
                    else -> books.sortedByDescending { it.durChapterTime }
                }
                returnData.setData(data)
            }
        }

    /**
     * 获取封面
     * 原版走 Glide + BookCover；桌面返回封面文件字节（HttpServer 按 image 响应）
     */
    fun getCover(parameters: Map<String, List<String>>): ReturnData {
        val returnData = ReturnData()
        val coverPath = parameters["path"]?.firstOrNull()
        if (coverPath.isNullOrEmpty()) {
            return returnData.setErrorMsg("path不能为空")
        }
        val bytes = runBlocking {
            if (coverPath.isAbsUrl()) {
                AnalyzeUrl(coverPath, callTimeout = 0, coroutineContext = currentCoroutineContext())
                    .getByteArrayAwait()
            } else if (File(coverPath).exists()) {
                File(coverPath).readBytes()
            } else {
                null
            }
        }
        if (bytes != null) {
            return returnData.setData(bytes)
        }
        // 原版默认封面占位；桌面无法生成时返回错误
        return returnData.setErrorMsg("封面获取失败")
    }

    /**
     * 获取正文图片
     * 原版 ImageProvider 缓存 + 解码 Bitmap；桌面返回图片文件字节
     */
    fun getImg(parameters: Map<String, List<String>>): ReturnData {
        val returnData = ReturnData()
        val bookUrl = parameters["url"]?.firstOrNull()
        if (bookUrl.isNullOrBlank()) {
            return returnData.setErrorMsg("bookUrl为空")
        }
        val src = parameters["path"]?.firstOrNull()
            ?: return returnData.setErrorMsg("图片链接为空")
        val width = parameters["width"]?.firstOrNull()?.toInt() ?: 640
        val book = appDb.bookDao.getBook(bookUrl)
            ?: return returnData.setErrorMsg("bookUrl不对")
        val bookSource = appDb.bookSourceDao.getBookSource(book.origin)
        val bytes = runBlocking {
            ImageProvider.cacheImage(book, src, bookSource)
            ImageProvider.getImageBytes(book, src, width)
        }
        return if (bytes != null) {
            returnData.setData(bytes)
        } else {
            returnData.setErrorMsg("图片获取失败")
        }
    }

    /**
     * 更新目录
     */
    fun refreshToc(parameters: Map<String, List<String>>): ReturnData {
        val returnData = ReturnData()
        try {
            val bookUrl = parameters["url"]?.firstOrNull()
            if (bookUrl.isNullOrEmpty()) {
                return returnData.setErrorMsg("参数url不能为空，请指定书籍地址")
            }
            val book = appDb.bookDao.getBook(bookUrl)
                ?: return returnData.setErrorMsg("未在数据库找到对应书籍，请先添加")
            if (book.isLocal) {
                val toc = LocalBook.getChapterList(book)
                appDb.bookChapterDao.delByBook(book.bookUrl)
                appDb.bookChapterDao.insert(*toc.toTypedArray())
                book.update()
                return returnData.setData(toc)
            } else {
                val bookSource = appDb.bookSourceDao.getBookSource(book.origin)
                    ?: return returnData.setErrorMsg("未找到对应书源,请换源")
                val toc = runBlocking {
                    if (book.tocUrl.isBlank()) {
                        WebBook.getBookInfoAwait(bookSource, book)
                    }
                    WebBook.getChapterListAwait(bookSource, book).getOrThrow()
                }
                appDb.bookChapterDao.delByBook(book.bookUrl)
                appDb.bookChapterDao.insert(*toc.toTypedArray())
                book.update()
                return returnData.setData(toc)
            }
        } catch (e: Exception) {
            return returnData.setErrorMsg(e.localizedMessage ?: "refresh toc error")
        }
    }

    /**
     * 获取目录
     */
    fun getChapterList(parameters: Map<String, List<String>>): ReturnData {
        val bookUrl = parameters["url"]?.firstOrNull()
        val returnData = ReturnData()
        if (bookUrl.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数url不能为空，请指定书籍地址")
        }
        val chapterList = appDb.bookChapterDao.getChapterList(bookUrl)
        if (chapterList.isEmpty()) {
            return refreshToc(parameters)
        }
        return returnData.setData(chapterList)
    }

    /**
     * 获取正文
     */
    fun getBookContent(parameters: Map<String, List<String>>): ReturnData {
        val bookUrl = parameters["url"]?.firstOrNull()
        val index = parameters["index"]?.firstOrNull()?.toInt()
        val returnData = ReturnData()
        if (bookUrl.isNullOrEmpty()) {
            return returnData.setErrorMsg("参数url不能为空，请指定书籍地址")
        }
        if (index == null) {
            return returnData.setErrorMsg("参数index不能为空, 请指定目录序号")
        }
        val book = appDb.bookDao.getBook(bookUrl)
        val chapter = runBlocking {
            var chapter = appDb.bookChapterDao.getChapter(bookUrl, index)
            var wait = 0
            while (chapter == null && wait < 30) {
                delay(1000)
                chapter = appDb.bookChapterDao.getChapter(bookUrl, index)
                wait++
            }
            chapter
        }
        if (book == null || chapter == null) {
            return returnData.setErrorMsg("未找到")
        }
        var content: String? = BookHelp.getContent(book, chapter)
        if (content != null) {
            val contentProcessor = ContentProcessor.get(book.name, book.origin)
            content = runBlocking {
                contentProcessor.getContent(book, chapter, content, includeTitle = false)
                    .toString()
            }
            return returnData.setData(content)
        }
        val bookSource = appDb.bookSourceDao.getBookSource(book.origin)
            ?: return returnData.setErrorMsg("未找到书源")
        try {
            content = runBlocking {
                WebBook.getContentAwait(bookSource, book, chapter).let {
                    val contentProcessor = ContentProcessor.get(book.name, book.origin)
                    contentProcessor.getContent(book, chapter, it, includeTitle = false)
                        .toString()
                }
            }
            returnData.setData(content)
        } catch (e: Exception) {
            returnData.setErrorMsg(e.stackTraceStr)
        }
        return returnData
    }

    /**
     * 保存书籍
     */
    suspend fun saveBook(postData: String?): ReturnData {
        val returnData = ReturnData()
        GSON.fromJsonObject<Book>(postData).getOrNull()?.let { book ->
            // 原 AppWebDav.uploadBookProgress(book)，WebDAV 同步可选（T6.4）
            book.save()
            return returnData.setData("")
        }
        return returnData.setErrorMsg("格式不对")
    }

    /**
     * 删除书籍
     */
    fun deleteBook(postData: String?): ReturnData {
        val returnData = ReturnData()
        GSON.fromJsonObject<Book>(postData).getOrNull()?.let { book ->
            book.delete()
            return returnData.setData("")
        }
        return returnData.setErrorMsg("格式不对")
    }

    /**
     * 保存进度
     */
    suspend fun saveBookProgress(postData: String?): ReturnData {
        val returnData = ReturnData()
        GSON.fromJsonObject<BookProgress>(postData)
            .onFailure { it.printOnDebug() }
            .getOrNull()?.let { bookProgress ->
                appDb.bookDao.getBook(bookProgress.name, bookProgress.author)?.let { book ->
                    book.durChapterIndex = bookProgress.durChapterIndex
                    book.durChapterPos = bookProgress.durChapterPos
                    book.durChapterTitle = bookProgress.durChapterTitle
                    book.durChapterTime = bookProgress.durChapterTime
                    // 原 AppWebDav.uploadBookProgress(bookProgress) + ReadBook 全局阅读状态，桌面版裁剪
                    book.update()
                    return returnData.setData("")
                }
            }
        return returnData.setErrorMsg("格式不对")
    }

    /**
     * 添加本地书籍
     */
    fun addLocalBook(
        parameters: Map<String, List<String>>,
        files: Map<String, String>
    ): ReturnData {
        val returnData = ReturnData()
        val rawFileName = parameters["fileName"]?.firstOrNull()
            ?: return returnData.setErrorMsg("fileName 不能为空")
        val fileName = kotlin.runCatching {
            requireSafeUploadedBookFileName(rawFileName)
        }.getOrElse {
            return returnData.setErrorMsg("fileName 格式不正确")
        }
        val fileData = files["fileData"]
            ?: return returnData.setErrorMsg("fileData 不能为空")
        kotlin.runCatching {
            val path = LocalBook.saveBookFile(File(fileData).inputStream(), fileName)
            LocalBook.importFile(path)
        }.onFailure {
            return when (it) {
                is SecurityException -> returnData.setErrorMsg("需重新设置书籍保存位置!")
                else -> returnData.setErrorMsg("保存书籍错误\n${it.localizedMessage}")
            }
        }
        return returnData.setData(true)
    }

    /**
     * 保存web阅读界面配置
     */
    fun saveWebReadConfig(postData: String?): ReturnData {
        val returnData = ReturnData()
        postData?.let {
            CacheManager.put("webReadConfig", postData)
        } ?: CacheManager.delete("webReadConfig")
        return returnData.setData("")
    }

    /**
     * 缓存书籍章节（对应原版章节列表"缓存"按钮；桌面直接驱动 CacheBook 协程）
     * body: {"bookUrl": "...", "start": 0, "end": 99}
     */
    fun cacheBook(postData: String?): ReturnData {
        val returnData = ReturnData()
        val map = GSON.fromJsonObject<Map<String, Any>>(postData).getOrNull()
            ?: return returnData.setErrorMsg("格式不对")
        val bookUrl = map["bookUrl"]?.toString() ?: return returnData.setErrorMsg("bookUrl 不能为空")
        val book = appDb.bookDao.getBook(bookUrl) ?: return returnData.setErrorMsg("未找到书籍")
        val start = (map["start"] as? Number)?.toInt() ?: 0
        val end = (map["end"] as? Number)?.toInt() ?: (book.totalChapterNum - 1).coerceAtLeast(start)
        CacheBook.start(book, start, end)
        return returnData.setData("")
    }

    /** 停止缓存（全部） */
    fun cacheBookStop(): ReturnData {
        CacheBook.stop()
        return ReturnData().setData("")
    }

    /** 移除单本书的缓存队列：body {"bookUrl": "..."} */
    fun cacheBookRemove(postData: String?): ReturnData {
        val returnData = ReturnData()
        val map = GSON.fromJsonObject<Map<String, Any>>(postData).getOrNull()
            ?: return returnData.setErrorMsg("格式不对")
        val bookUrl = map["bookUrl"]?.toString() ?: return returnData.setErrorMsg("bookUrl 不能为空")
        CacheBook.remove(bookUrl)
        return returnData.setData("")
    }

    /**
     * 获取web阅读界面配置
     */
    fun getWebReadConfig(): ReturnData {
        val returnData = ReturnData()
        val data = CacheManager.get("webReadConfig")
            ?: return returnData.setErrorMsg("没有配置")
        return returnData.setData(data)
    }

}
