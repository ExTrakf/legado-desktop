package io.legado.desktop.help.storage

import io.legado.desktop.constant.AppLog
import io.legado.desktop.constant.PreferKey
import io.legado.desktop.data.appDb
import io.legado.desktop.data.entities.AutoTaskRule
import io.legado.desktop.data.entities.Book
import io.legado.desktop.data.entities.BookGroup
import io.legado.desktop.data.entities.BookHighlight
import io.legado.desktop.data.entities.BookSource
import io.legado.desktop.data.entities.Bookmark
import io.legado.desktop.data.entities.DictRule
import io.legado.desktop.data.entities.HighlightRule
import io.legado.desktop.data.entities.HttpTTS
import io.legado.desktop.data.entities.KeyboardAssist
import io.legado.desktop.data.entities.ReadRecord
import io.legado.desktop.data.entities.ReplaceRule
import io.legado.desktop.data.entities.RssSource
import io.legado.desktop.data.entities.RssStar
import io.legado.desktop.data.entities.RuleSub
import io.legado.desktop.data.entities.SearchKeyword
import io.legado.desktop.data.entities.Server
import io.legado.desktop.data.entities.TxtTocRule
import io.legado.desktop.env.DesktopEnv
import io.legado.desktop.help.DirectLinkUpload
import io.legado.desktop.help.HighlightStyle
import io.legado.desktop.help.SimpleACache
import io.legado.desktop.help.book.isLocal
import io.legado.desktop.help.book.upType
import io.legado.desktop.help.config.LocalConfig
import io.legado.desktop.help.config.ReadBookConfig
import io.legado.desktop.model.BookCover
import io.legado.desktop.model.localBook.LocalBook
import io.legado.desktop.utils.FileUtils
import io.legado.desktop.utils.GSON
import io.legado.desktop.utils.LogUtils
import io.legado.desktop.utils.MD5Utils
import io.legado.desktop.utils.compress.ZipUtils
import io.legado.desktop.utils.fromJsonArray
import io.legado.desktop.utils.fromJsonObject
import io.legado.desktop.utils.getPrefString
import io.legado.desktop.utils.isJsonArray
import io.legado.desktop.utils.printOnDebug
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

/**
 * 恢复（与原版等价；主题/壁纸/启动图标/视频配置/定时任务调度/Toast 为裁剪项）
 */
object Restore {

    private const val TAG = "Restore"

    /** 桌面设备 id（原 Android androidId，按安装目录稳定） */
    private val androidId: String by lazy {
        MD5Utils.md5Encode(DesktopEnv.homeDir.toString())
    }

    suspend fun restoreFromFile(filePath: String) {
        LogUtils.d(TAG, "开始恢复备份 path:$filePath")
        kotlin.runCatching {
            FileUtils.delete(Backup.backupPath)
            ZipUtils.unZipToPath(File(filePath), Backup.backupPath)
        }.onFailure {
            AppLog.put("复制解压文件出错\n${it.localizedMessage}", it)
            return
        }
        kotlin.runCatching {
            restore(Backup.backupPath)
            LocalConfig.lastBackup = System.currentTimeMillis()
        }.onFailure {
            AppLog.put("恢复备份出错\n${it.localizedMessage}", it)
        }
    }

    private suspend fun restore(path: String) {
        val aes = BackupAES()
        val backupRoot = File(path)
        val restoredAutoTasks = fileToListT<AutoTaskRule>(path, "autoTask.json")
        fileToListT<Book>(path, "bookshelf.json")?.let {
            it.forEach { book ->
                book.upType()
                book.customCoverUrl = book.customCoverUrl?.let { coverPath ->
                    remapRestoredCoverPath(coverPath, backupRoot, DesktopEnv.homeDir.toFile())
                }
            }
            it.filter { book -> book.isLocal }
                .forEach { book ->
                    book.coverUrl = LocalBook.getCoverPath(book)
                }
            val newBooks = arrayListOf<Book>()
            val ignoreLocalBook = BackupConfig.ignoreLocalBook
            it.forEach { book ->
                if (ignoreLocalBook && book.isLocal) {
                    return@forEach
                }
                if (appDb.bookDao.has(book.bookUrl)) {
                    try {
                        appDb.bookDao.update(book)
                    } catch (_: Exception) {
                        appDb.bookDao.insert(book)
                    }
                } else {
                    newBooks.add(book)
                }
            }
            appDb.bookDao.insert(*newBooks.toTypedArray())
        }
        fileToListT<Bookmark>(path, "bookmark.json")?.let {
            appDb.bookmarkDao.insert(*it.toTypedArray())
        }
        fileToListT<BookHighlight>(path, "highlight.json")?.let { highlights ->
            kotlin.runCatching {
                applyLegacyHighlightStyles(File(path, "highlight.json").readText(), highlights)
                applyLegacyHighlightOwners(highlights)
                appDb.bookHighlightDao.insert(*highlights.toTypedArray())
            }.onFailure {
                AppLog.put("恢复高亮出错\n${it.localizedMessage}", it)
            }
        }
        fileToListT<HighlightRule>(path, "highlightRule.json")?.let { rules ->
            kotlin.runCatching {
                appDb.highlightRuleDao.replaceAll(rules.map(HighlightRule::normalizeForRestore))
            }.onFailure {
                AppLog.put("恢复高亮规则出错\n${it.localizedMessage}", it)
            }
        }
        fileToListT<BookGroup>(path, "bookGroup.json")?.let {
            appDb.bookGroupDao.insert(*it.toTypedArray())
        }
        fileToListT<BookSource>(path, "bookSource.json")?.let {
            appDb.bookSourceDao.insert(*it.toTypedArray())
        } ?: run {
            val bookSourceFile = File(path, "bookSource.json")
            if (bookSourceFile.exists()) {
                val json = bookSourceFile.readText()
                ImportOldData.importOldSource(json)
            }
        }
        fileToListT<RssSource>(path, "rssSources.json")?.let {
            appDb.rssSourceDao.insert(*it.toTypedArray())
        }
        fileToListT<RssStar>(path, "rssStar.json")?.let {
            appDb.rssStarDao.insert(*it.toTypedArray())
        }
        fileToListT<ReplaceRule>(path, "replaceRule.json")?.let {
            appDb.replaceRuleDao.insert(*it.toTypedArray())
        }
        fileToListT<SearchKeyword>(path, "searchHistory.json")?.let {
            appDb.searchKeywordDao.insert(*it.toTypedArray())
        }
        fileToListT<RuleSub>(path, "sourceSub.json")?.let {
            appDb.ruleSubDao.insert(*it.toTypedArray())
        }
        fileToListT<TxtTocRule>(path, "txtTocRule.json")?.let {
            appDb.txtTocRuleDao.insert(*it.toTypedArray())
        }
        fileToListT<HttpTTS>(path, "httpTTS.json")?.let {
            appDb.httpTTSDao.insert(*it.toTypedArray())
        }
        fileToListT<DictRule>(path, "dictRule.json")?.let {
            appDb.dictRuleDao.insert(*it.toTypedArray())
        }
        fileToListT<KeyboardAssist>(path, "keyboardAssists.json")?.let {
            appDb.keyboardAssistsDao.deleteAll() //先删除所有,保证和备份数据一样
            appDb.keyboardAssistsDao.insert(*it.toTypedArray())
        }
        fileToListT<ReadRecord>(path, "readRecord.json")?.let {
            it.forEach { readRecord ->
                //判断是不是本机记录
                if (readRecord.deviceId != androidId) {
                    appDb.readRecordDao.insert(readRecord)
                } else {
                    val current = appDb.readRecordDao
                        .getRecord(readRecord.deviceId, readRecord.bookName)
                    if (current == null || current.readTime < readRecord.readTime) {
                        appDb.readRecordDao.insert(readRecord)
                    } else if (readRecord.author.isNotBlank()) {
                        appDb.readRecordDao.insert(current.copy(author = readRecord.author))
                    }
                }
            }
        }
        File(path, "servers.json").takeIf {
            it.exists()
        }?.runCatching {
            var json = readText()
            if (!json.isJsonArray()) {
                json = aes.decryptStr(json)
            }
            GSON.fromJsonArray<Server>(json).getOrNull()?.let {
                appDb.serverDao.insert(*it.toTypedArray())
            }
        }?.onFailure {
            AppLog.put("恢复服务器配置出错\n${it.localizedMessage}", it)
        }
        File(path, DirectLinkUpload.ruleFileName).takeIf {
            it.exists()
        }?.runCatching {
            val json = readText()
            SimpleACache.put(DirectLinkUpload.ruleFileName, json)
        }?.onFailure {
            AppLog.put("恢复直链上传出错\n${it.localizedMessage}", it)
        }
        File(path, BookCover.configFileName).takeIf {
            it.exists()
        }?.runCatching {
            val json = readText()
            BookCover.saveCoverRule(json)
        }?.onFailure {
            AppLog.put("恢复封面规则出错\n${it.localizedMessage}", it)
        }
        if (!BackupConfig.ignoreReadConfig) {
            //恢复阅读界面配置
            File(path, ReadBookConfig.configFileName).takeIf {
                it.exists()
            }?.runCatching {
                FileUtils.delete(ReadBookConfig.configFilePath)
                copyTo(File(ReadBookConfig.configFilePath))
                ReadBookConfig.initConfigs()
            }?.onFailure {
                AppLog.put("恢复阅读界面出错\n${it.localizedMessage}", it)
            }
            File(path, ReadBookConfig.shareConfigFileName).takeIf {
                it.exists()
            }?.runCatching {
                FileUtils.delete(ReadBookConfig.shareConfigFilePath)
                copyTo(File(ReadBookConfig.shareConfigFilePath))
                ReadBookConfig.initShareConfig()
            }?.onFailure {
                AppLog.put("恢复阅读界面出错\n${it.localizedMessage}", it)
            }
        }
        File(path, "config.xml").takeIf {
            it.exists()
        }?.runCatching {
            PrefsXml.read(this).forEach { (key, value) ->
                if (BackupConfig.keyIsNotIgnore(key)) {
                    if (key == PreferKey.webDavPassword) {
                        kotlin.runCatching {
                            aes.decryptStr(value.toString())
                        }.getOrNull()?.let {
                            DesktopEnv.putPrefRaw(key, it)
                        } ?: run {
                            if (getPrefString(PreferKey.webDavPassword).isNullOrBlank()) {
                                DesktopEnv.putPrefRaw(key, value)
                            }
                        }
                    } else {
                        DesktopEnv.putPrefRaw(key, value)
                    }
                }
            }
        }?.onFailure {
            AppLog.put("恢复配置出错\n${it.localizedMessage}", it)
        }
        restoreBackupMediaDirectory(File(path), DesktopEnv.homeDir.toFile(), "covers")
            .onFailure {
                AppLog.put("恢复封面图片出错\n${it.localizedMessage}", it)
            }
        if (!restoredAutoTasks.isNullOrEmpty()) {
            appDb.autoTaskRuleDao.upsert(*restoredAutoTasks.toTypedArray())
        }
    }

    private inline fun <reified T> fileToListT(path: String, fileName: String): List<T>? {
        try {
            val file = File(path, fileName)
            if (file.exists()) {
                LogUtils.d(TAG, "阅读恢复备份 $fileName 文件大小 ${file.length()}")
                FileInputStream(file).use {
                    return GSON.fromJsonArray<T>(it).getOrThrow().also { list ->
                        LogUtils.d(TAG, "阅读恢复备份 $fileName 列表大小 ${list.size}")
                    }
                }
            } else {
                LogUtils.d(TAG, "阅读恢复备份 $fileName 文件不存在")
            }
        } catch (e: Exception) {
            AppLog.put("$fileName\n读取解析出错\n${e.localizedMessage}", e)
            e.printOnDebug()
        }
        return null
    }

    private fun applyLegacyHighlightOwners(highlights: List<BookHighlight>) {
        highlights.forEach { highlight ->
            val bookUrl = highlight.bookUrl.ifBlank {
                appDb.bookDao.getBook(highlight.bookName, highlight.bookAuthor)?.bookUrl.orEmpty()
            }
            val chapterUrl = highlight.chapterUrl.ifBlank {
                appDb.bookChapterDao.getChapter(bookUrl, highlight.chapterIndex)
                    ?.takeIf { it.title == highlight.chapterName }
                    ?.url
                    .orEmpty()
            }
            highlight.bindLegacyOwner(bookUrl, chapterUrl)
        }
    }

}

internal fun applyLegacyHighlightStyles(json: String, highlights: List<BookHighlight>) {
    val legacy = GSON.fromJsonObject<List<Map<String, Any?>>>(json).getOrNull()
    highlights.forEachIndexed { index, highlight ->
        if (highlight.style.isNullOrBlank()) {
            val raw = legacy?.getOrNull(index)
            val fill = (raw?.get("bgColor") as? Number)?.toInt() ?: 0
            val textColor = (raw?.get("textColor") as? Number)?.toInt() ?: 0
            if (fill != 0 || textColor != 0) {
                highlight.applyStyle(HighlightStyle(fill = fill, textColor = textColor))
            }
        }
    }
}
