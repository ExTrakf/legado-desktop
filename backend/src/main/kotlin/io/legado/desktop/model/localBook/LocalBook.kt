package io.legado.desktop.model.localBook

import com.script.ScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.legado.desktop.constant.AppLog
import io.legado.desktop.constant.AppPattern
import io.legado.desktop.constant.BookType
import io.legado.desktop.data.appDb
import io.legado.desktop.data.entities.BaseSource
import io.legado.desktop.data.entities.Book
import io.legado.desktop.data.entities.BookChapter
import io.legado.desktop.exception.EmptyFileException
import io.legado.desktop.exception.NoBooksDirException
import io.legado.desktop.exception.NoStackTraceException
import io.legado.desktop.exception.TocEmptyException
import io.legado.desktop.help.book.BookHelp
import io.legado.desktop.help.book.ContentProcessor
import io.legado.desktop.help.book.addType
import io.legado.desktop.help.book.archiveName
import io.legado.desktop.help.book.cacheLocalUri
import io.legado.desktop.help.book.getArchiveUri
import io.legado.desktop.help.book.getLocalUri
import io.legado.desktop.help.book.isArchive
import io.legado.desktop.help.book.isEpub
import io.legado.desktop.help.book.isMobi
import io.legado.desktop.help.book.isPdf
import io.legado.desktop.help.book.isUmd
import io.legado.desktop.help.book.removeLocalUriCache
import io.legado.desktop.help.book.simulatedTotalChapterNum
import io.legado.desktop.help.config.AppConfig
import io.legado.desktop.model.analyzeRule.AnalyzeUrl
import io.legado.desktop.utils.ArchiveUtils
import io.legado.desktop.utils.FileUtils
import io.legado.desktop.utils.GSON
import io.legado.desktop.utils.MD5Utils
import io.legado.desktop.utils.exists
import io.legado.desktop.utils.fromJsonObject
import io.legado.desktop.utils.getFile
import io.legado.desktop.utils.isAbsUrl
import io.legado.desktop.utils.isDataUrl
import io.legado.desktop.utils.printOnDebug
import kotlinx.coroutines.currentCoroutineContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Base64
import java.util.concurrent.CancellationException
import java.util.regex.Pattern

internal fun isMissingLocalBookFile(
    isContentUri: Boolean,
    localFileExists: Boolean,
    error: Throwable,
): Boolean {
    if (error is SecurityException || error is CancellationException) return false
    return if (isContentUri) {
        error is FileNotFoundException ||
                error is IllegalArgumentException &&
                error.message?.contains(FileNotFoundException::class.java.name) == true
    } else {
        !localFileExists
    }
}

internal fun resolveLocalBookOutputFile(root: File, relativePath: String): File {
    val canonicalRoot = root.canonicalFile
    val outputFile = File(canonicalRoot, relativePath).canonicalFile
    if (!outputFile.toPath().startsWith(canonicalRoot.toPath())) {
        throw SecurityException("书籍文件只能保存到指定路径")
    }
    return outputFile
}

internal fun prepareLocalBookOutputFile(root: File, relativePath: String): File {
    var outputFile = resolveLocalBookOutputFile(root, relativePath)
    val parent = outputFile.parentFile
    if (parent != null && !parent.exists() && !parent.mkdirs()) {
        throw FileNotFoundException("Unable to create book directory: $parent")
    }
    outputFile = resolveLocalBookOutputFile(root, relativePath)
    return outputFile
}

/**
 * 书籍文件导入 目录正文解析
 * 支持在线文件(txt epub umd 压缩文件 本地文件
 * 桌面版：Uri/SAF → 本地文件路径（String）
 */
object LocalBook {

    private val nameAuthorPatterns = arrayOf(
        Pattern.compile("(.*?)《([^《》]+)》.*?作者：(.*)"),
        Pattern.compile("(.*?)《([^《》]+)》(.*)"),
        Pattern.compile("(^)(.+) 作者：(.+)$"),
        Pattern.compile("(^)(.+) by (.+)$")
    )

    @Throws(FileNotFoundException::class, SecurityException::class)
    fun getBookInputStream(book: Book): InputStream {
        val path = book.getLocalUri()
            ?: throw FileNotFoundException("${book.bookUrl} 文件不存在")
        val file = File(path)
        if (file.exists()) {
            return FileInputStream(file)
        }
        val readError = FileNotFoundException("${path} 文件不存在")
        if (!isMissingLocalBookFile(
                isContentUri = false,
                localFileExists = file.exists(),
                error = readError,
            )
        ) {
            throw readError
        }
        book.removeLocalUriCache()
        val localArchivePath = book.getArchiveUri()
        if (localArchivePath != null) {
            restoreArchiveBookFile(book, localArchivePath)
            return getBookInputStream(book)
        }
        if (downloadRemoteBook(book)) {
            return getBookInputStream(book)
        }
        throw FileNotFoundException("${path} 文件不存在").apply {
            addSuppressed(readError)
        }
    }

    fun getLastModified(book: Book): Result<Long> {
        return kotlin.runCatching {
            val path = book.getLocalUri()
                ?: throw FileNotFoundException("${book.bookUrl} 文件不存在")
            val file = File(path)
            if (file.exists()) {
                return@runCatching file.lastModified()
            }
            throw FileNotFoundException("${path} 文件不存在")
        }
    }

    @Throws(TocEmptyException::class)
    fun getChapterList(book: Book): ArrayList<BookChapter> {
        val chapters = when {
            book.isEpub -> {
                EpubFile.getChapterList(book)
            }

            book.isUmd -> {
                UmdFile.getChapterList(book)
            }

            book.isPdf -> {
                PdfFile.getChapterList(book)
            }

            book.isMobi -> {
                MobiFile.getChapterList(book)
            }

            else -> {
                TextFile.getChapterList(book)
            }
        }
        if (chapters.isEmpty()) {
            throw TocEmptyException("目录列表为空") // 原 R.string.chapter_list_empty
        }
        val list = ArrayList(LinkedHashSet(chapters))
        list.forEachIndexed { index, bookChapter ->
            bookChapter.index = index
            if (bookChapter.title.isEmpty()) {
                bookChapter.title = "无标题章节"
            }
        }
        val replaceRules = ContentProcessor.get(book).getTitleReplaceRules()
        val replaceBook = book.toReplaceBook()
        book.durChapterTitle = list.getOrElse(book.durChapterIndex) { list.last() }
            .getDisplayTitle(
                replaceRules,
                book.getUseReplaceRule(),
                replaceBook = replaceBook
            )
        book.latestChapterTitle =
            list.getOrElse(book.simulatedTotalChapterNum() - 1) { list.last() }
                .getDisplayTitle(
                    replaceRules,
                    book.getUseReplaceRule(),
                    replaceBook = replaceBook
                )
        book.totalChapterNum = list.size
        book.latestChapterTime = System.currentTimeMillis()
        return list
    }

    fun getContent(book: Book, chapter: BookChapter): String? {
        var content = try {
            when {
                book.isEpub -> {
                    EpubFile.getContent(book, chapter)
                }

                book.isUmd -> {
                    UmdFile.getContent(book, chapter)
                }

                book.isPdf -> {
                    PdfFile.getContent(book, chapter)
                }

                book.isMobi -> {
                    MobiFile.getContent(book, chapter)
                }

                else -> {
                    TextFile.getContent(book, chapter)
                }
            }
        } catch (e: Exception) {
            e.printOnDebug()
            AppLog.put("获取本地书籍内容失败\n${e.localizedMessage}", e)
            "获取本地书籍内容失败\n${e.localizedMessage}"
        }
        if (book.isEpub) {
            content ?: return null
            if (content.indexOf('&') > -1) {
                content = content.replace("&lt;img", "&lt; img", true)
                return org.apache.commons.text.StringEscapeUtils.unescapeHtml4(content)
            }
        }

        if (content.isNullOrEmpty() && !chapter.isVolume) {
            return null
        }

        return content
    }

    fun getCoverPath(book: Book): String {
        return getCoverPath(book.bookUrl)
    }

    private fun getCoverPath(bookUrl: String): String {
        return FileUtils.getPath(
            File(io.legado.desktop.env.DesktopEnv.homeDir.toFile(), "covers").absolutePath,
            "${MD5Utils.md5Encode16(bookUrl)}.jpg"
        )
    }

    /**
     * 下载在线的文件并自动导入到阅读（txt umd epub)
     */
    suspend fun importFileOnLine(
        str: String,
        fileName: String,
        source: BaseSource? = null,
    ): Book {
        return importFile(saveBookFile(str, fileName, source))
    }

    /**
     * 导入本地文件
     */
    fun importFile(filePath: String): Book {
        val bookUrl: String
        //updateTime变量不要修改,否则会导致读取不到缓存
        val file = File(filePath).also {
            if (it.length() == 0L) throw EmptyFileException("Unexpected empty File")
            bookUrl = it.absolutePath
        }
        var book = appDb.bookDao.getBook(bookUrl)
        if (book == null) {
            val nameAuthor = analyzeNameAuthor(file.name)
            book = Book(
                type = BookType.text or BookType.local,
                bookUrl = bookUrl,
                name = nameAuthor.first,
                author = nameAuthor.second,
                originName = file.name,
                latestChapterTime = file.lastModified(),
                order = appDb.bookDao.minOrder - 1
            )
            upBookInfo(book)
            if (appDb.bookDao.insertIgnore(book) == -1L) {
                throw NoStackTraceException(
                    "本地书籍与已有书籍书名、作者相同" // 原 R.string.local_book_identity_conflict
                )
            }
        } else {
            withParserCacheInvalidated(book) {
                deleteBook(book, false)
                upBookInfo(book)
                // 触发 isLocalModified
                book.latestChapterTime = 0
                //已有书籍说明是更新,删除原有目录
                appDb.bookChapterDao.delByBook(bookUrl)
            }
        }
        return book
    }

    fun upBookInfo(book: Book) {
        when {
            book.isEpub -> EpubFile.upBookInfo(book)
            book.isUmd -> UmdFile.upBookInfo(book)
            book.isPdf -> PdfFile.upBookInfo(book)
            book.isMobi -> MobiFile.upBookInfo(book)
        }
    }

    internal fun <T> withParserCacheInvalidated(book: Book, action: () -> T): T {
        return withParserCacheInvalidated(book.bookUrl, book.originName, action)
    }

    internal fun <T> withParserCacheInvalidated(
        bookUrl: String,
        fileName: String,
        action: () -> T,
    ): T {
        return when {
            fileName.endsWith(".epub", true) -> synchronized(EpubFile) {
                EpubFile.clear(bookUrl)
                action()
            }

            fileName.endsWith(".umd", true) -> synchronized(UmdFile) {
                UmdFile.clear(bookUrl)
                action()
            }

            fileName.endsWith(".pdf", true) -> synchronized(PdfFile) {
                PdfFile.clear(bookUrl)
                action()
            }

            fileName.endsWith(".mobi", true) ||
                fileName.endsWith(".azw3", true) ||
                fileName.endsWith(".azw", true) -> synchronized(MobiFile) {
                MobiFile.clear(bookUrl)
                action()
            }

            else -> action()
        }
    }

    /* 导入压缩包内的书籍 */
    fun importArchiveFile(
        archiveFilePath: String,
        saveFileName: String? = null,
        filter: ((String) -> Boolean)? = null
    ): List<Book> {
        val files = ArchiveUtils.deCompress(archiveFilePath, filter = filter)
        if (files.isEmpty()) {
            throw NoStackTraceException("压缩包内没有可导入的书籍文件") // 原 R.string.unsupport_archivefile_entry
        }
        return files.map {
            saveBookFile(FileInputStream(it), saveFileName ?: it.name).let { path ->
                importFile(path).apply {
                    //附加压缩包名称 以便解压文件被删后再解压
                    origin = "${BookType.localTag}::${File(archiveFilePath).name}"
                    addType(BookType.archive)
                    save()
                }
            }
        }
    }

    /* 批量导入 支持自动导入压缩包的支持书籍 */
    fun importFiles(filePath: String): List<Book> {
        val books = mutableListOf<Book>()
        val file = File(filePath)
        if (ArchiveUtils.isArchive(file.name)) {
            books.addAll(
                importArchiveFile(filePath) {
                    it.matches(AppPattern.bookFileRegex)
                }
            )
        } else {
            books.add(importFile(filePath))
        }
        return books
    }

    /**
     * 从文件分析书籍必要信息（书名 作者等）
     */
    private fun analyzeNameAuthor(fileName: String): Pair<String, String> {
        val tempFileName = fileName.substringBeforeLast(".")
        var name = ""
        var author = ""
        if (!AppConfig.bookImportFileName.isNullOrBlank()) {
            try {
                //在用户脚本后添加捕获author、name的代码，只要脚本中author、name有值就会被捕获
                val js =
                    AppConfig.bookImportFileName + "\nJSON.stringify({author:author,name:name})"
                //在脚本中定义如何分解文件名成书名、作者名
                val jsonStr = RhinoScriptEngine.run {
                    val bindings = ScriptBindings()
                    bindings["src"] = tempFileName
                    eval(js, bindings)
                }.toString()
                val bookMess = GSON.fromJsonObject<HashMap<String, String>>(jsonStr)
                    .getOrThrow()
                name = bookMess["name"] ?: ""
                author = bookMess["author"]?.takeIf { it.length != tempFileName.length } ?: ""
            } catch (e: Exception) {
                AppLog.put("执行导入文件名规则出错\n${e.localizedMessage}", e)
            }
        }
        if (name.isBlank()) {
            for (pattern in nameAuthorPatterns) {
                pattern.matcher(tempFileName).takeIf { it.find() }?.run {
                    name = group(2)!!
                    val group1 = group(1) ?: ""
                    val group3 = group(3) ?: ""
                    author = BookHelp.formatBookAuthor(group1 + group3)
                    return Pair(name, author)
                }
            }
            name = BookHelp.formatBookName(tempFileName)
            author = BookHelp.formatBookAuthor(tempFileName.replace(name, ""))
                .takeIf { it.length != tempFileName.length } ?: ""
        }
        return Pair(name, author)
    }

    fun deleteBook(book: Book, deleteOriginal: Boolean) {
        kotlin.runCatching {
            BookHelp.clearCache(book)
            if (!book.coverUrl.isNullOrEmpty()) {
                FileUtils.delete(book.coverUrl!!)
            }
            if (deleteOriginal) {
                book.getLocalUri()?.let { File(it).delete() }
            }
        }
    }

    /**
     * 下载在线的文件
     */
    suspend fun saveBookFile(
        str: String,
        fileName: String,
        source: BaseSource? = null,
    ): String {
        val inputStream = when {
            str.isAbsUrl() -> AnalyzeUrl(
                str, source = source, callTimeout = 0,
                coroutineContext = currentCoroutineContext()
            ).getInputStreamAwait()

            str.isDataUrl() -> ByteArrayInputStream(
                Base64.getDecoder().decode(
                    str.substringAfter("base64,")
                )
            )

            else -> throw NoStackTraceException("在线导入书籍支持http/https/DataURL")
        }
        return saveBookFile(inputStream, fileName)
    }

    @Throws(SecurityException::class)
    fun saveBookFile(
        inputStream: InputStream,
        fileName: String
    ): String {
        val defaultBookTreeUri = AppConfig.defaultBookTreeUri
            ?: io.legado.desktop.env.DesktopEnv.booksDir.toString()
        val treeFile = File(defaultBookTreeUri)
        return inputStream.use {
            try {
                val file = prepareLocalBookOutputFile(treeFile, fileName)
                withParserCacheInvalidated(file.absolutePath, fileName) {
                    FileOutputStream(file).use { oStream ->
                        it.copyTo(oStream)
                    }
                }
                file.absolutePath
            } catch (e: FileNotFoundException) {
                throw SecurityException("请重新设置书籍保存位置\nPermission Denial\n$e").apply {
                    addSuppressed(e)
                }
            }
        }
    }

    fun isOnBookShelf(
        fileName: String
    ): Boolean {
        return appDb.bookDao.hasFile(fileName)
    }

    private fun restoreArchiveBookFile(book: Book, archivePath: String) {
        val extractedFile = ArchiveUtils.deCompress(
            archivePath,
            filter = { it.contains(book.originName) }
        ).firstOrNull()
            ?: throw NoStackTraceException("压缩包内没有可导入的书籍文件") // 原 R.string.unsupport_archivefile_entry
        val filePath = saveBookFile(FileInputStream(extractedFile), book.originName)
        withParserCacheInvalidated(book) {
            book.cacheLocalUri(filePath)
        }
    }

    //文件类书源 合并在线书籍信息 在线 > 本地
    fun mergeBook(localBook: Book, onLineBook: Book?): Book {
        onLineBook ?: return localBook
        localBook.name = onLineBook.name.ifBlank { localBook.name }
        localBook.author = onLineBook.author.ifBlank { localBook.author }
        localBook.coverUrl = onLineBook.coverUrl
        localBook.intro =
            if (onLineBook.intro.isNullOrBlank()) localBook.intro else onLineBook.intro
        localBook.save()
        return localBook
    }

    // 下载 book 对应的远程文件并绑定本地路径（原 WebDAV 自动恢复，桌面版 WebDAV 可选不移植 → false）
    internal fun downloadRemoteBook(localBook: Book): Boolean {
        return false
    }

}
