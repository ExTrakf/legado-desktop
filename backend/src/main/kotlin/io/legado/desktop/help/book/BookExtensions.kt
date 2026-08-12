@file:Suppress("unused")

package io.legado.desktop.help.book

import com.script.buildScriptBindings
import com.script.rhino.RhinoScriptEngine
import io.legado.desktop.constant.AppLog
import io.legado.desktop.constant.AppPattern
import io.legado.desktop.constant.BookSourceType
import io.legado.desktop.constant.BookType
import io.legado.desktop.data.appDb
import io.legado.desktop.data.entities.BaseBook
import io.legado.desktop.data.entities.Book
import io.legado.desktop.data.entities.BookChapter
import io.legado.desktop.data.entities.BookSource
import io.legado.desktop.exception.NoStackTraceException
import io.legado.desktop.help.RuleBigDataHelp
import io.legado.desktop.help.config.AppConfig
import io.legado.desktop.model.analyzeRule.CustomUrl
import io.legado.desktop.model.localBook.LocalBook
import io.legado.desktop.utils.GSON
import io.legado.desktop.utils.MD5Utils
import io.legado.desktop.utils.isUri
import io.legado.desktop.utils.normalizeFileName
import java.io.File
import java.time.LocalDate
import java.time.Period.between
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import io.legado.desktop.utils.LogUtils


val Book.isAudio: Boolean
    get() = isType(BookType.audio)

val Book.isVideo: Boolean
    get() = isType(BookType.video)

val Book.isImage: Boolean
    get() = isType(BookType.image)

val Book.isLocal: Boolean
    get() {
        if (type == 0) {
            return origin == BookType.localTag || origin.startsWith(BookType.webDavTag)
        }
        return isType(BookType.local)
    }

val Book.isLocalTxt: Boolean
    get() = isLocal && originName.endsWith(".txt", true)

val Book.isEpub: Boolean
    get() = isLocal && originName.endsWith(".epub", true)

val Book.isUmd: Boolean
    get() = isLocal && originName.endsWith(".umd", true)

val Book.isPdf: Boolean
    get() = isLocal && originName.endsWith(".pdf", true)

val Book.isMobi: Boolean
    get() = isLocal && (originName.endsWith(".mobi", true) ||
            originName.endsWith(".azw3", true) ||
            originName.endsWith(".azw", true))

val Book.isOnLineTxt: Boolean
    get() = !isLocal && isType(BookType.text)

val Book.isWebFile: Boolean
    get() = isType(BookType.webFile)

val Book.isUpError: Boolean
    get() = isType(BookType.updateError)

val Book.isArchive: Boolean
    get() = isType(BookType.archive)

val Book.isNotShelf: Boolean
    get() = isType(BookType.notShelf)

val Book.archiveName: String
    get() {
        if (!isArchive) throw NoStackTraceException("Book is not deCompressed from archive")
        // local_book::archive.rar
        // webDav::https://...../archive.rar
        val archivePath = origin.substringAfter("::").let {
            if (origin.startsWith(BookType.webDavTag)) CustomUrl(it).getUrl() else it
        }
        return archivePath.substringAfterLast("/")
    }

fun Book.contains(word: String?): Boolean {
    if (word.isNullOrEmpty()) {
        return true
    }
    return name.contains(word)
            || author.contains(word)
            || originName.contains(word)
            || origin.contains(word)
            || kind?.contains(word) == true
            || intro?.contains(word) == true
}

private val localUriCache = ConcurrentHashMap<String, String>()

/** 桌面版：本地书籍路径（String）。缓存 + 书库目录回退查找，语义对齐原版 getLocalUri */
fun Book.getLocalUri(): String? {
    if (!isLocal) {
        throw NoStackTraceException("不是本地书籍")
    }
    var path = localUriCache[bookUrl]
    if (path != null) {
        return path
    }
    path = if (bookUrl.isUri()) {
        bookUrl.removePrefix("file://")
    } else {
        bookUrl
    }
    if (File(path).exists()) {
        cacheLocalUri(path)
        return path
    }
    // bookUrl 路径失效时，尝试在书籍保存目录下按文件名查找
    val defaultBookDir = AppConfig.defaultBookTreeUri
        ?: io.legado.desktop.env.DesktopEnv.booksDir.toString()
    findLocalBookFile(File(defaultBookDir), originName, 5)?.let {
        cacheLocalUri(it.absolutePath)
        return it.absolutePath
    }
    cacheLocalUri(path)
    return path
}

fun Book.getArchiveUri(): String? {
    val defaultBookDir = AppConfig.defaultBookTreeUri
        ?: io.legado.desktop.env.DesktopEnv.booksDir.toString()
    return if (isArchive) {
        findLocalBookFile(File(defaultBookDir), archiveName)?.absolutePath
    } else {
        null
    }
}

fun Book.cacheLocalUri(path: String) {
    localUriCache[bookUrl] = path
}

fun Book.removeLocalUriCache() {
    localUriCache.remove(bookUrl)
}

/** 在目录及其子目录（depth 层内）中查找指定文件名的文件 */
private fun findLocalBookFile(root: File, fileName: String, depth: Int = 5): File? {
    if (!root.isDirectory) return null
    root.listFiles()?.forEach { f ->
        if (f.isFile) {
            if (f.name == fileName) return f
        } else if (depth > 0) {
            findLocalBookFile(f, fileName, depth - 1)?.let { return it }
        }
    }
    return null
}

fun Book.getRemoteUrl(): String? {
    if (origin.startsWith(BookType.webDavTag)) {
        return origin.substring(BookType.webDavTag.length)
    }
    return null
}

fun Book.setType(vararg types: Int) {
    type = 0
    addType(*types)
}

fun Book.addType(vararg types: Int) {
    types.forEach {
        type = type or it
    }
}

fun Book.removeType(vararg types: Int) {
    types.forEach {
        type = type and it.inv()
    }
}

fun Book.removeAllBookType() {
    removeType(BookType.allBookType)
}

fun Book.clearType() {
    type = 0
}

fun Book.isType(bookType: Int): Boolean = type and bookType > 0

fun Book.upType() {
    if (type < 4) {
        type = when (type) {
            BookSourceType.image -> BookType.image
            BookSourceType.audio -> BookType.audio
            BookSourceType.file -> BookType.webFile
            else -> BookType.text
        }
        if (origin == BookType.localTag || origin.startsWith(BookType.webDavTag)) {
            type = type or BookType.local
        }
    }
}

fun Book.sync(currentBook: Book, toc: List<BookChapter>) {
    val chapterIndex = BookHelp.getDurChapter(currentBook, toc)
    currentBook.updateTo(this)
    if (toc.isEmpty()) return
    durChapterIndex = chapterIndex
    durChapterTitle = toc[chapterIndex].getDisplayTitle(
        ContentProcessor.get(this).getTitleReplaceRules(),
        getUseReplaceRule(),
        replaceBook = toReplaceBook()
    )
}

fun Book.update() {
    appDb.bookDao.updatePreservingCustomCoverUrl(this)
}

fun Book.savePreservingCustomCoverUrl() {
    if (appDb.bookDao.has(bookUrl)) {
        appDb.bookDao.updatePreservingCustomCoverUrl(this)
    } else {
        appDb.bookDao.insert(this)
    }
}

fun Book.primaryStr(): String {
    return origin + bookUrl
}

fun Book.updateTo(newBook: Book): Book {
    newBook.durChapterIndex = durChapterIndex
    newBook.durChapterTitle = durChapterTitle
    newBook.durVolumeIndex = durVolumeIndex
    newBook.chapterInVolumeIndex = chapterInVolumeIndex
    newBook.durChapterPos = durChapterPos
    newBook.durChapterTime = durChapterTime
    newBook.group = group
    newBook.order = order
    newBook.customCoverUrl = customCoverUrl
    newBook.customIntro = customIntro
    newBook.customTag = customTag
    newBook.canUpdate = canUpdate
    newBook.readConfig = readConfig
    newBook.syncTime = syncTime
    newBook.type = (newBook.type and BookType.allBookType) or
        (type and BookType.allBookType.inv())
    val variableMap = variableMap.toMutableMap()
    variableMap.keys.removeIf {
        newBook.hasVariable(it)
    }
    newBook.variableMap.putAll(variableMap)
    newBook.variable = GSON.toJson(newBook.variableMap)
    return newBook
}

fun Book.hasVariable(key: String): Boolean {
    return variableMap.contains(key) || RuleBigDataHelp.hasBookVariable(bookUrl, key)
}

fun Book.getFolderNameNoCache(): String {
    return name.replace(AppPattern.fileNameRegex, "").let {
        it.substring(0, min(9, it.length)) + MD5Utils.md5Encode16(bookUrl)
    }
}

fun Book.getBookSource(): BookSource? {
    return appDb.bookSourceDao.getBookSource(origin)
}

fun Book.isLocalModified(): Boolean {
    return isLocal && LocalBook.getLastModified(this).getOrDefault(0L) > latestChapterTime
}

fun Book.releaseHtmlData() {
    infoHtml = null
    tocHtml = null
}

fun Book.isSameNameAuthor(other: Any?): Boolean {
    if (other is BaseBook) {
        return name == other.name && author == other.author
    }
    return false
}

internal fun normalizeExportFileName(name: String, suffix: String): String {
    return "$name.$suffix".normalizeFileName()
}

internal fun parseExportFileNameResult(result: Any?): String? {
    return result?.toString()?.takeIf { it.isNotBlank() }
}

fun Book.getExportFileName(suffix: String): String {
    val default = normalizeExportFileName("$name 作者：${getRealAuthor()}", suffix)
    val jsStr = AppConfig.bookExportFileName
    if (jsStr.isNullOrBlank()) {
        return default
    }
    val bindings = buildScriptBindings { bindings ->
        bindings["epubIndex"] = ""// 兼容老版本,修复可能存在的错误
        bindings["name"] = name
        bindings["author"] = getRealAuthor()
    }
    return kotlin.runCatching {
        val customName = parseExportFileNameResult(RhinoScriptEngine.eval(jsStr, bindings))
            ?: return@runCatching default
        normalizeExportFileName(customName, suffix)
    }.onFailure {
        AppLog.put("导出书名规则错误,使用默认规则\n${it.localizedMessage}", it)
    }.getOrDefault(default)
}

/**
 * 获取分割文件后的文件名
 */
fun Book.getExportFileName(
    suffix: String,
    epubIndex: Int,
    jsStr: String? = AppConfig.episodeExportFileName
): String {
    // 默认规则
    val default = normalizeExportFileName(
        "$name 作者：${getRealAuthor()} [${epubIndex}]",
        suffix,
    )
    if (jsStr.isNullOrBlank()) {
        return default
    }
    val bindings = buildScriptBindings { bindings ->
        bindings["name"] = name
        bindings["author"] = getRealAuthor()
        bindings["epubIndex"] = epubIndex
    }
    return kotlin.runCatching {
        val customName = parseExportFileNameResult(RhinoScriptEngine.eval(jsStr, bindings))
            ?: return@runCatching default
        normalizeExportFileName(customName, suffix)
    }.onFailure {
        AppLog.put("导出书名规则错误,使用默认规则\n${it.localizedMessage}", it)
    }.getOrDefault(default)
}

// 根据当前日期计算章节总数
fun Book.simulatedTotalChapterNum(): Int {
    return if (readSimulating()) {
        val currentDate = LocalDate.now()
        val daysPassed = between(config.startDate, currentDate).days + 1
        // 计算当前应该解锁到哪一章
        val chaptersToUnlock =
            max(0, (config.startChapter ?: 0) + (daysPassed * config.dailyChapters))
        min(totalChapterNum, chaptersToUnlock)
    } else {
        totalChapterNum
    }
}

fun Book.readSimulating(): Boolean {
    return config.readSimulating
}

fun Book.readProgress(): Float? {
    if (durChapterIndex == 0 && durChapterPos == 0) return null
    val chapterCount = simulatedTotalChapterNum()
    if (chapterCount <= 1) return 1f
    val lastChapterIndex = chapterCount - 1
    return (durChapterIndex.toFloat() / lastChapterIndex).coerceIn(0f, 1f)
}

fun tryParesExportFileName(jsStr: String): Boolean {
    val bindings = buildScriptBindings { bindings ->
        bindings["name"] = "name"
        bindings["author"] = "author"
        bindings["epubIndex"] = "epubIndex"
    }
    return runCatching {
        parseExportFileNameResult(RhinoScriptEngine.eval(jsStr, bindings)) != null
    }.getOrDefault(false)
}
