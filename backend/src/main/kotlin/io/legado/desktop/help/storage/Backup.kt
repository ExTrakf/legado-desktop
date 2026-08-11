package io.legado.desktop.help.storage

import io.legado.desktop.constant.AppLog
import io.legado.desktop.constant.PreferKey
import io.legado.desktop.data.appDb
import io.legado.desktop.env.DesktopEnv
import io.legado.desktop.help.DirectLinkUpload
import io.legado.desktop.help.config.LocalConfig
import io.legado.desktop.help.config.ReadBookConfig
import io.legado.desktop.model.BookCover
import io.legado.desktop.utils.FileUtils
import io.legado.desktop.utils.GSON
import io.legado.desktop.utils.LogUtils
import io.legado.desktop.utils.compress.ZipUtils
import io.legado.desktop.utils.normalizeFileName
import io.legado.desktop.utils.writeToOutputStream
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备份（与原版等价；AppWebDav/视频配置/主题为裁剪项）
 */
object Backup {

    val backupPath: String by lazy {
        FileUtils.getPath(DesktopEnv.cacheDir.toFile(), "backup")
    }
    val zipFilePath = "${DesktopEnv.homeDir}${File.separator}tmp_backup.zip"

    private const val TAG = "Backup"

    private val backupFileNames by lazy {
        arrayOf(
            "bookshelf.json",
            "bookmark.json",
            "highlight.json",
            "highlightRule.json",
            "bookGroup.json",
            "bookSource.json",
            "rssSources.json",
            "rssStar.json",
            "replaceRule.json",
            "readRecord.json",
            "searchHistory.json",
            "sourceSub.json",
            "txtTocRule.json",
            "httpTTS.json",
            "keyboardAssists.json",
            "dictRule.json",
            "autoTask.json",
            "servers.json",
            DirectLinkUpload.ruleFileName,
            ReadBookConfig.configFileName,
            ReadBookConfig.shareConfigFileName,
            BookCover.configFileName,
            "config.xml"
        )
    }

    fun getNowZipFileName(): String {
        val backupDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Date(System.currentTimeMillis()))
        val deviceName = io.legado.desktop.help.config.AppConfig.webDavDeviceName
        return if (deviceName?.isNotBlank() == true) {
            "backup${backupDate}-${deviceName}.zip"
        } else {
            "backup${backupDate}.zip"
        }.normalizeFileName()
    }

    private fun shouldBackup(): Boolean {
        val lastBackup = LocalConfig.lastBackup
        return lastBackup + 24 * 60 * 60 * 1000L < System.currentTimeMillis()
    }

    suspend fun backupLocked(path: String?) {
        withContext(IO) {
            backup(path)
        }
    }

    suspend fun backup(path: String?) {
        LogUtils.d(TAG, "开始备份 path:$path")
        LocalConfig.lastBackup = System.currentTimeMillis()
        val aes = BackupAES()
        FileUtils.delete(backupPath)
        writeListToJson(appDb.bookDao.all, "bookshelf.json", backupPath)
        writeListToJson(appDb.bookmarkDao.all, "bookmark.json", backupPath)
        writeListToJson(appDb.bookHighlightDao.all, "highlight.json", backupPath)
        writeListToJson(
            appDb.highlightRuleDao.all,
            "highlightRule.json",
            backupPath,
            writeEmpty = true
        )
        writeListToJson(appDb.bookGroupDao.all, "bookGroup.json", backupPath)
        writeListToJson(appDb.bookSourceDao.all, "bookSource.json", backupPath)
        writeListToJson(appDb.rssSourceDao.all, "rssSources.json", backupPath)
        writeListToJson(appDb.rssStarDao.all, "rssStar.json", backupPath)
        writeListToJson(appDb.replaceRuleDao.all, "replaceRule.json", backupPath)
        writeListToJson(appDb.readRecordDao.all, "readRecord.json", backupPath)
        writeListToJson(appDb.searchKeywordDao.all, "searchHistory.json", backupPath)
        writeListToJson(appDb.ruleSubDao.all, "sourceSub.json", backupPath)
        writeListToJson(appDb.txtTocRuleDao.all, "txtTocRule.json", backupPath)
        writeListToJson(appDb.httpTTSDao.all, "httpTTS.json", backupPath)
        writeListToJson(appDb.keyboardAssistsDao.all, "keyboardAssists.json", backupPath)
        writeListToJson(appDb.dictRuleDao.all, "dictRule.json", backupPath)
        writeListToJson(appDb.autoTaskRuleDao.all(), "autoTask.json", backupPath)
        GSON.toJson(appDb.serverDao.all).let { json ->
            aes.runCatching {
                encryptBase64(json)
            }.getOrDefault(json).let {
                FileUtils.createFileIfNotExist(backupPath + File.separator + "servers.json")
                    .writeText(it)
            }
        }
        currentCoroutineContext().ensureActive()
        GSON.toJson(ReadBookConfig.configList).let {
            FileUtils.createFileIfNotExist(backupPath + File.separator + ReadBookConfig.configFileName)
                .writeText(it)
        }
        GSON.toJson(ReadBookConfig.shareConfig).let {
            FileUtils.createFileIfNotExist(backupPath + File.separator + ReadBookConfig.shareConfigFileName)
                .writeText(it)
        }
        DirectLinkUpload.getConfig()?.let {
            FileUtils.createFileIfNotExist(backupPath + File.separator + DirectLinkUpload.ruleFileName)
                .writeText(GSON.toJson(it))
        }
        BookCover.getConfig()?.let {
            FileUtils.createFileIfNotExist(backupPath + File.separator + BookCover.configFileName)
                .writeText(GSON.toJson(it))
        }
        currentCoroutineContext().ensureActive()
        // 配置导出（原 Android SharedPreferences XML → 桌面 config.xml 等价格式）
        val configFile = FileUtils.createFileIfNotExist(backupPath + File.separator + "config.xml")
        PrefsXml.write(configFile, DesktopEnv.allPrefs().filterKeys { key ->
            BackupConfig.keyIsNotIgnore(key)
        }.toMutableMap().apply {
            DesktopEnv.getPrefString(PreferKey.webDavPassword).takeIf { it.isNotBlank() }?.let {
                put(PreferKey.webDavPassword, aes.runCatching { encryptBase64(it) }.getOrDefault(it))
            }
        })
        currentCoroutineContext().ensureActive()
        val zipFileName = getNowZipFileName()
        val paths = arrayListOf(*backupFileNames)
        for (i in 0 until paths.size) {
            paths[i] = backupPath + File.separator + paths[i]
        }
        FileUtils.delete(zipFilePath)
        FileUtils.delete(zipFilePath.replace("tmp_", ""))
        val backupFileName = if (io.legado.desktop.help.config.AppConfig.onlyLatestBackup) {
            "backup.zip"
        } else {
            zipFileName
        }
        if (ZipUtils.zipFiles(paths, zipFilePath)) {
            when {
                path.isNullOrBlank() -> {
                    copyBackup(DesktopEnv.homeDir.toFile(), backupFileName)
                }

                else -> {
                    copyBackup(File(path), backupFileName)
                }
            }
        }
        FileUtils.delete(backupPath)
        FileUtils.delete(zipFilePath)
        currentCoroutineContext().ensureActive()
    }

    private suspend fun writeListToJson(
        list: List<Any>,
        fileName: String,
        path: String,
        writeEmpty: Boolean = false
    ) {
        currentCoroutineContext().ensureActive()
        withContext(IO) {
            if (list.isNotEmpty() || writeEmpty) {
                LogUtils.d(TAG, "阅读备份 $fileName 列表大小 ${list.size}")
                val file = FileUtils.createFileIfNotExist(path + File.separator + fileName)
                file.outputStream().buffered().use {
                    GSON.writeToOutputStream(it, list)
                }
                LogUtils.d(TAG, "阅读备份 $fileName 写入大小 ${file.length()}")
            } else {
                LogUtils.d(TAG, "阅读备份 $fileName 列表为空")
            }
        }
    }

    @Throws(Exception::class)
    private fun copyBackup(rootFile: File, fileName: String) {
        FileInputStream(File(zipFilePath)).use { inputS ->
            val file = FileUtils.createFileIfNotExist(rootFile, fileName)
            file.outputStream().use { outputS ->
                inputS.copyTo(outputS)
            }
        }
    }

    fun clearCache() {
        FileUtils.delete(backupPath)
        FileUtils.delete(zipFilePath)
    }
}
