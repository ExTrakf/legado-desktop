package io.legado.desktop.help.config

import io.legado.desktop.env.DesktopEnv
import io.legado.desktop.constant.AppConst
import io.legado.desktop.constant.PreferKey
import io.legado.desktop.data.appDb
import io.legado.desktop.utils.GSON
import io.legado.desktop.utils.fromJsonObject
import io.legado.desktop.utils.getPrefBoolean
import io.legado.desktop.utils.getPrefInt
import io.legado.desktop.utils.getPrefLong
import io.legado.desktop.utils.getPrefString
import io.legado.desktop.utils.parseIpsFromString
import io.legado.desktop.utils.parseIpsFromList
import io.legado.desktop.utils.putPrefBoolean
import io.legado.desktop.utils.putPrefInt
import io.legado.desktop.utils.putPrefLong
import io.legado.desktop.utils.putPrefString
import io.legado.desktop.utils.removePref
import java.net.InetAddress
import io.legado.desktop.utils.LogUtils

@Suppress("MemberVisibilityCanBePrivate", "ConstPropertyName")
object AppConfig {
    private const val JS_SOURCE_API_PREFS = "js_source_api_credentials"
    private const val JS_SOURCE_API_TOKEN = "token"

    val isCronet = getPrefBoolean(PreferKey.cronet)
    var useAntiAlias = getPrefBoolean(PreferKey.antiAlias)
    var userAgent: String = getPrefUserAgent()
    var customHosts = getPrefString(PreferKey.customHosts)
    var editTheme = getPrefInt(PreferKey.editTheme, 0)
    var editThemeDark = getPrefInt(PreferKey.editThemeDark, 0)
    var editTemeAuto = getPrefBoolean(PreferKey.editTemeAuto)
    var isEInkMode = getPrefString(PreferKey.themeMode) == "3"
    var clickActionTL = getPrefInt(PreferKey.clickActionTL, 2)
    var clickActionTC = getPrefInt(PreferKey.clickActionTC, 2)
    var clickActionTR = getPrefInt(PreferKey.clickActionTR, 1)
    var clickActionML = getPrefInt(PreferKey.clickActionML, 2)
    var clickActionMC = getPrefInt(PreferKey.clickActionMC, 0)
    var clickActionMR = getPrefInt(PreferKey.clickActionMR, 1)
    var clickActionBL = getPrefInt(PreferKey.clickActionBL, 2)
    var clickActionBC = getPrefInt(PreferKey.clickActionBC, 1)
    var clickActionBR = getPrefInt(PreferKey.clickActionBR, 1)
    var themeMode = getPrefString(PreferKey.themeMode, "0")
    var useDefaultCover = getPrefBoolean(PreferKey.useDefaultCover, false)
    var optimizeRender = false // 桌面版无 CanvasRecorder，渲染优化不启用
    var recordLog = getPrefBoolean(PreferKey.recordLog)
    var recordHttpLog = getPrefBoolean(PreferKey.recordHttpLog)
    var editFontScale = getPrefInt(PreferKey.editFontScale, 16)
    var editNonPrintable = getPrefInt(PreferKey.editNonPrintable, 0)
    var editAutoWrap = getPrefBoolean(PreferKey.editAutoWrap, true)
    var editAutoComplete = getPrefBoolean(PreferKey.editAutoComplete, true)
    var showBoardLine = getPrefInt(PreferKey.showBoardLine, 1).coerceIn(1, 5)
    var adaptSpecialStyle = getPrefBoolean(PreferKey.adaptSpecialStyle, true)



    //dns配置
    private var _hostMap: Map<String, Any?>? = null
    val hostMap: Map<String, Any?>
        get() = _hostMap ?: run {
            val cache = GSON.fromJsonObject<Map<String, Any?>>(customHosts).getOrNull() ?: emptyMap()
            _hostMap = cache
            cache
        }
    private var _addressCache: Map<String, List<InetAddress>>? = null
    val addressCache: Map<String, List<InetAddress>>
        get() = _addressCache ?: run {
            val cache = hostMap.mapNotNull { (host, ipValue) ->
                val addresses = when (ipValue) {
                    is String -> ipValue.parseIpsFromString()
                    is List<*> -> ipValue.parseIpsFromList()
                    else -> null
                }
                addresses?.let { host to it }
            }.toMap()
            _addressCache = cache
            cache
        }
    private fun List<*>.parseIpsFromList(): List<InetAddress> =
        mapNotNull { element ->
            (element as? String)?.trim()?.takeIf { it.isNotEmpty() }
                ?.runCatching { InetAddress.getByName(this) }
                ?.getOrNull()
        }

    var isNightTheme: Boolean
        get() = when (themeMode) {
            "1" -> false
            "2" -> true
            "3" -> false
            else -> false // 桌面版无系统夜间模式检测
        }
        set(value) {
            if (isNightTheme != value) {
                if (value) {
                    putPrefString(PreferKey.themeMode, "2")
                } else {
                    putPrefString(PreferKey.themeMode, "1")
                }
            }
        }
    var showBookname: Int
        get() = getPrefInt(PreferKey.showBooknameLayout, 0)
        set(value) {
            putPrefInt(PreferKey.showBooknameLayout, value)
        }
    var bookshelfMargin: Int
        get() = getPrefInt(PreferKey.bookshelfMargin, 12)
        set(value) {
            putPrefInt(PreferKey.bookshelfMargin, value)
        }

    var showUnread: Boolean
        get() = getPrefBoolean(PreferKey.showUnread, true)
        set(value) {
            putPrefBoolean(PreferKey.showUnread, value)
        }

    var showLastUpdateTime: Boolean
        get() = getPrefBoolean(PreferKey.showLastUpdateTime, false)
        set(value) {
            putPrefBoolean(PreferKey.showLastUpdateTime, value)
        }

    var showSearchReadRecord: Boolean
        get() = getPrefBoolean(PreferKey.showSearchReadRecord, true)
        set(value) {
            putPrefBoolean(PreferKey.showSearchReadRecord, value)
        }

    var showBookshelfReadProgress: Boolean
        get() = getPrefBoolean(PreferKey.showBookshelfReadProgress, true)
        set(value) {
            putPrefBoolean(PreferKey.showBookshelfReadProgress, value)
        }

    var showBookshelfRecentReading: Boolean
        get() = getPrefBoolean(PreferKey.showBookshelfRecentReading, false)
        set(value) {
            putPrefBoolean(PreferKey.showBookshelfRecentReading, value)
        }

    var showBookshelfStats: Boolean
        get() = getPrefBoolean(PreferKey.showBookshelfStats, false)
        set(value) {
            putPrefBoolean(PreferKey.showBookshelfStats, value)
        }

    var showWaitUpCount: Boolean
        get() = getPrefBoolean(PreferKey.showWaitUpCount, false)
        set(value) {
            putPrefBoolean(PreferKey.showWaitUpCount, value)
        }

    var readBrightness: Int
        get() = if (isNightTheme) {
            getPrefInt(PreferKey.nightBrightness, 100)
        } else {
            getPrefInt(PreferKey.brightness, 100)
        }
        set(value) {
            if (isNightTheme) {
                putPrefInt(PreferKey.nightBrightness, value)
            } else {
                putPrefInt(PreferKey.brightness, value)
            }
        }

    val textSelectAble: Boolean
        get() = getPrefBoolean(PreferKey.textSelectAble, true)

    val isTransparentStatusBar: Boolean
        get() = getPrefBoolean(PreferKey.transparentStatusBar, true)

    val immNavigationBar: Boolean
        get() = getPrefBoolean(PreferKey.immNavigationBar, true)

    val screenOrientation: String?
        get() = getPrefString(PreferKey.screenOrientation)

    var bookGroupStyle: Int
        get() = getPrefInt(PreferKey.bookGroupStyle, 0)
        set(value) {
            putPrefInt(PreferKey.bookGroupStyle, value)
        }

    var bookshelfLayout: Int
        get() = getPrefInt(PreferKey.bookshelfLayout, 0)
        set(value) {
            putPrefInt(PreferKey.bookshelfLayout, value)
        }

    var saveTabPosition: Int
        get() = getPrefInt(PreferKey.saveTabPosition, 0)
        set(value) {
            putPrefInt(PreferKey.saveTabPosition, value)
        }

    var bookExportFileName: String?
        get() = getPrefString(PreferKey.bookExportFileName)
        set(value) {
            putPrefString(PreferKey.bookExportFileName, value)
        }

    // 保存 自定义导出章节模式 文件名js表达式
    var episodeExportFileName: String?
        get() = getPrefString(PreferKey.episodeExportFileName, "")
        set(value) {
            putPrefString(PreferKey.episodeExportFileName, value)
        }

    var bookImportFileName: String?
        get() = getPrefString(PreferKey.bookImportFileName)
        set(value) {
            putPrefString(PreferKey.bookImportFileName, value)
        }

    var backupPath: String?
        get() = getPrefString(PreferKey.backupPath)
        set(value) {
            if (value.isNullOrEmpty()) {
                removePref(PreferKey.backupPath)
            } else {
                putPrefString(PreferKey.backupPath, value)
            }
        }

    // 书籍保存位置
    var defaultBookTreeUri: String?
        get() = getPrefString(PreferKey.defaultBookTreeUri).takeIf { it.isNotBlank() } // 空白=未设置（对齐 Android getString 未设置返回 null）
        set(value) {
            if (value.isNullOrEmpty()) {
                removePref(PreferKey.defaultBookTreeUri)
            } else {
                putPrefString(PreferKey.defaultBookTreeUri, value)
            }
        }

    val showDiscovery: Boolean
        get() = getPrefBoolean(PreferKey.showDiscovery, true)

    val showRSS: Boolean
        get() = getPrefBoolean(PreferKey.showRss, true)

    val autoRefreshBook: Boolean
        get() = getPrefBoolean(PreferKey.autoRefresh)

    val onlyUpdateRead: Boolean
        get() = getPrefBoolean(PreferKey.onlyUpdateRead)

    var enableReview: Boolean
        get() = getPrefBoolean(PreferKey.enableReview, false)
        set(value) {
            putPrefBoolean(PreferKey.enableReview, value)
        }

    var threadCount: Int
        get() = getPrefInt(PreferKey.threadCount, 32)
        set(value) {
            putPrefInt(PreferKey.threadCount, value)
        }

    var remoteServerId: Long
        get() = getPrefLong(PreferKey.remoteServerId)
        set(value) {
            putPrefLong(PreferKey.remoteServerId, value)
        }

    // 添加本地选择的目录
    var importBookPath: String?
        get() = getPrefString("importBookPath")
        set(value) {
            if (value == null) {
                removePref("importBookPath")
            } else {
                putPrefString("importBookPath", value)
            }
        }

    var ttsFlowSys: Boolean
        get() = getPrefBoolean(PreferKey.ttsFollowSys, true)
        set(value) {
            putPrefBoolean(PreferKey.ttsFollowSys, value)
        }

    val noAnimScrollPage: Boolean
        get() = getPrefBoolean(PreferKey.noAnimScrollPage, false)

    const val defaultSpeechRate = 5

    var ttsSpeechRate: Int
        get() = getPrefInt(PreferKey.ttsSpeechRate, defaultSpeechRate)
        set(value) {
            putPrefInt(PreferKey.ttsSpeechRate, value)
        }

    var ttsTimer: Int
        get() = getPrefInt(PreferKey.ttsTimer, 0)
        set(value) {
            putPrefInt(PreferKey.ttsTimer, value)
        }

    var sleepTimerPreferChapter: Boolean
        get() = getPrefBoolean(PreferKey.sleepTimerPreferChapter, false)
        set(value) {
            putPrefBoolean(PreferKey.sleepTimerPreferChapter, value)
        }

    val speechRatePlay: Int get() = if (ttsFlowSys) defaultSpeechRate else ttsSpeechRate

    var chineseConverterType: Int
        get() = getPrefInt(PreferKey.chineseConverterType)
        set(value) {
            putPrefInt(PreferKey.chineseConverterType, value)
        }

    var systemTypefaces: Int
        get() = getPrefInt(PreferKey.systemTypefaces)
        set(value) {
            putPrefInt(PreferKey.systemTypefaces, value)
        }

    var elevation: Int
        get() = if (isEInkMode) 0 else getPrefInt(
            PreferKey.barElevation,
            0
        )
        set(value) {
            putPrefInt(PreferKey.barElevation, value)
        }

    var readUrlInBrowser: Boolean
        get() = getPrefBoolean(PreferKey.readUrlOpenInBrowser)
        set(value) {
            putPrefBoolean(PreferKey.readUrlOpenInBrowser, value)
        }

    var exportCharset: String
        get() {
            val c = getPrefString(PreferKey.exportCharset)
            if (c.isNullOrBlank()) {
                return "UTF-8"
            }
            return c
        }
        set(value) {
            putPrefString(PreferKey.exportCharset, value)
        }

    var exportUseReplace: Boolean
        get() = getPrefBoolean(PreferKey.exportUseReplace, true)
        set(value) {
            putPrefBoolean(PreferKey.exportUseReplace, value)
        }

    var exportToWebDav: Boolean
        get() = getPrefBoolean(PreferKey.exportToWebDav)
        set(value) {
            putPrefBoolean(PreferKey.exportToWebDav, value)
        }
    var exportNoChapterName: Boolean
        get() = getPrefBoolean(PreferKey.exportNoChapterName)
        set(value) {
            putPrefBoolean(PreferKey.exportNoChapterName, value)
        }

    // 是否启用自定义导出 default->false
    var enableCustomExport: Boolean
        get() = getPrefBoolean(PreferKey.enableCustomExport, false)
        set(value) {
            putPrefBoolean(PreferKey.enableCustomExport, value)
        }

    var exportType: Int
        get() = getPrefInt(PreferKey.exportType)
        set(value) {
            putPrefInt(PreferKey.exportType, value)
        }
    var exportPictureFile: Boolean
        get() = getPrefBoolean(PreferKey.exportPictureFile, false)
        set(value) {
            putPrefBoolean(PreferKey.exportPictureFile, value)
        }

    var parallelExportBook: Boolean
        get() = getPrefBoolean(PreferKey.parallelExportBook, false)
        set(value) {
            putPrefBoolean(PreferKey.parallelExportBook, value)
        }

    var changeSourceCheckAuthor: Boolean
        get() = getPrefBoolean(PreferKey.changeSourceCheckAuthor)
        set(value) {
            putPrefBoolean(PreferKey.changeSourceCheckAuthor, value)
        }

    var ttsEngine: String?
        get() = getPrefString(PreferKey.ttsEngine)
        set(value) {
            putPrefString(PreferKey.ttsEngine, value)
        }

    var webPort: Int
        get() = getPrefInt(PreferKey.webPort, 1122)
        set(value) {
            putPrefInt(PreferKey.webPort, value)
        }

    var mcpPort: Int
        get() = getPrefInt(PreferKey.mcpPort, 1236)
        set(value) {
            putPrefInt(PreferKey.mcpPort, value)
        }

    var jsSourceApiToken: String?
        get() = DesktopEnv.getPrefString(JS_SOURCE_API_PREFS, "").ifBlank { null }
        set(value) {
            val normalizedValue = normalizeJsSourceApiToken(value)
            if (normalizedValue == null) {
                DesktopEnv.removePref(JS_SOURCE_API_PREFS)
            } else {
                DesktopEnv.putPrefString(JS_SOURCE_API_PREFS, normalizedValue)
            }
        }

    var tocUiUseReplace: Boolean
        get() = getPrefBoolean(PreferKey.tocUiUseReplace)
        set(value) {
            putPrefBoolean(PreferKey.tocUiUseReplace, value)
        }

    var tocCountWords: Boolean
        get() = getPrefBoolean(PreferKey.tocCountWords, true)
        set(value) {
            putPrefBoolean(PreferKey.tocCountWords, value)
        }

    var enableReadRecord: Boolean
        get() = getPrefBoolean(PreferKey.enableReadRecord, true)
        set(value) {
            putPrefBoolean(PreferKey.enableReadRecord, value)
        }

    val autoChangeSource: Boolean
        get() = getPrefBoolean(PreferKey.autoChangeSource, true)

    var changeSourceLoadInfo: Boolean
        get() = getPrefBoolean(PreferKey.changeSourceLoadInfo)
        set(value) {
            putPrefBoolean(PreferKey.changeSourceLoadInfo, value)
        }

    var changeSourceLoadToc: Boolean
        get() = getPrefBoolean(PreferKey.changeSourceLoadToc)
        set(value) {
            putPrefBoolean(PreferKey.changeSourceLoadToc, value)
        }

    var changeSourceLoadWordCount: Boolean
        get() = getPrefBoolean(PreferKey.changeSourceLoadWordCount) ||
                getPrefBoolean(PreferKey.changeSourceSortRespondTime) ||
                getPrefInt(PreferKey.changeSourceWordCountFilterMode) != 0
        set(value) {
            putPrefBoolean(PreferKey.changeSourceLoadWordCount, value)
            if (!value) {
                changeSourceSortRespondTime = false
                changeSourceWordCountFilterMode = 0
            }
        }

    var changeSourceSortRespondTime: Boolean
        get() = getPrefBoolean(PreferKey.changeSourceSortRespondTime)
        set(value) {
            putPrefBoolean(PreferKey.changeSourceSortRespondTime, value)
        }

    var changeSourceWordCountFilterMode: Int
        get() = getPrefInt(PreferKey.changeSourceWordCountFilterMode).coerceIn(0, 2)
        set(value) {
            val mode = value.coerceIn(0, 2)
            putPrefInt(PreferKey.changeSourceWordCountFilterMode, mode)
        }

    var changeSourceWordCountFilterMin: Int
        get() = getPrefInt(PreferKey.changeSourceWordCountFilterMin)
        set(value) {
            putPrefInt(PreferKey.changeSourceWordCountFilterMin, value.coerceAtLeast(0))
        }

    var changeSourceWordCountFilterMax: Int
        get() = getPrefInt(PreferKey.changeSourceWordCountFilterMax)
        set(value) {
            putPrefInt(PreferKey.changeSourceWordCountFilterMax, value.coerceAtLeast(0))
        }

    var openBookInfoByClickTitle: Boolean
        get() = getPrefBoolean(PreferKey.openBookInfoByClickTitle, true)
        set(value) {
            putPrefBoolean(PreferKey.openBookInfoByClickTitle, value)
        }

    var showBookshelfFastScroller: Boolean
        get() = getPrefBoolean(PreferKey.showBookshelfFastScroller, false)
        set(value) {
            putPrefBoolean(PreferKey.showBookshelfFastScroller, value)
        }

    var contentSelectSpeakMod: Int
        get() = getPrefInt(PreferKey.contentSelectSpeakMod)
        set(value) {
            putPrefInt(PreferKey.contentSelectSpeakMod, value)
        }

    var batchChangeSourceDelay: Int
        get() = getPrefInt(PreferKey.batchChangeSourceDelay)
        set(value) {
            putPrefInt(PreferKey.batchChangeSourceDelay, value)
        }

    val importKeepName get() = getPrefBoolean(PreferKey.importKeepName)
    val importKeepGroup get() = getPrefBoolean(PreferKey.importKeepGroup)
    var importKeepEnable: Boolean
        get() = getPrefBoolean(PreferKey.importKeepEnable, false)
        set(value) {
            putPrefBoolean(PreferKey.importKeepEnable, value)
        }
    var importShowComment: Boolean
        get() = getPrefBoolean(PreferKey.importShowComment, false)
        set(value) {
            putPrefBoolean(PreferKey.importShowComment, value)
        }

    val clickImgWay: String?
        get() = getPrefString(PreferKey.clickImgWay)

    val highlightActionByLongPress: Boolean
        get() = getPrefString(PreferKey.highlightActionTrigger, "click") == "longPress"

    var preDownloadNum
        get() = getPrefInt(PreferKey.preDownloadNum, 2)
        set(value) {
            putPrefInt(PreferKey.preDownloadNum, value)
        }

    val syncBookProgress get() = getPrefBoolean(PreferKey.syncBookProgress, true)

    val syncBookProgressPlus get() = getPrefBoolean(PreferKey.syncBookProgressPlus, false)

    val mediaButtonOnExit get() = getPrefBoolean("mediaButtonOnExit", true)

    val readAloudByMediaButton
        get() = getPrefBoolean(PreferKey.readAloudByMediaButton, false)

    val readAloudFollowManualPage
        get() = getPrefBoolean(PreferKey.readAloudFollowManualPage, false)

    val replaceEnableDefault get() = getPrefBoolean(PreferKey.replaceEnableDefault, true)

    val webDavDir get() = getPrefString(PreferKey.webDavDir, "legado")

    val webDavDeviceName get() = getPrefString(PreferKey.webDavDeviceName, "legado-desktop")

    val webDavBookAutoRestore
        get() = getPrefBoolean(PreferKey.webDavBookAutoRestore, false)

    val recordHeapDump get() = getPrefBoolean(PreferKey.recordHeapDump, false)

    val loadCoverOnlyWifi get() = getPrefBoolean(PreferKey.loadCoverOnlyWifi, false)

    val showAddToShelfAlert get() = getPrefBoolean(PreferKey.showAddToShelfAlert, true)

    val ignoreAudioFocus get() = getPrefBoolean(PreferKey.ignoreAudioFocus, false)

    var pauseReadAloudWhilePhoneCalls
        get() = getPrefBoolean(PreferKey.pauseReadAloudWhilePhoneCalls, false)
        set(value) = putPrefBoolean(PreferKey.pauseReadAloudWhilePhoneCalls, value)

    val onlyLatestBackup get() = getPrefBoolean(PreferKey.onlyLatestBackup, true)

    val autoCheckNewBackup get() = getPrefBoolean(PreferKey.autoCheckNewBackup, true)

    val autoBackup get() = getPrefBoolean(PreferKey.autoBackup, true)

    val defaultHomePage get() = getPrefString(PreferKey.defaultHomePage, "bookshelf")

    val updateToVariant get() = getPrefString(PreferKey.updateToVariant, "default_version")

    val streamReadAloudAudio get() = getPrefBoolean(PreferKey.streamReadAloudAudio, false)

    var audioSkipOpenCredits: Int
        get() = getPrefInt(PreferKey.audioSkipOpenCredits, 0)
        set(value) = putPrefInt(PreferKey.audioSkipOpenCredits, value.coerceAtLeast(0))

    var audioSkipCloseCredits: Int
        get() = getPrefInt(PreferKey.audioSkipCloseCredits, 0)
        set(value) = putPrefInt(PreferKey.audioSkipCloseCredits, value.coerceAtLeast(0))

    var audioCacheTreeUri: String?
        get() = getPrefString(PreferKey.audioCacheTreeUri)
        set(value) {
            if (value.isNullOrBlank()) {
                removePref(PreferKey.audioCacheTreeUri)
            } else {
                putPrefString(PreferKey.audioCacheTreeUri, value)
            }
        }

    val doublePageHorizontal: String?
        get() = getPrefString(PreferKey.doublePageHorizontal)

    val progressBarBehavior: String?
        get() = getPrefString(PreferKey.progressBarBehavior, "page")

    val keyPageOnLongPress
        get() = getPrefBoolean(PreferKey.keyPageOnLongPress, false)

    val volumeKeyPage
        get() = getPrefBoolean(PreferKey.volumeKeyPage, true)

    val volumeKeyPageOnPlay
        get() = getPrefBoolean(PreferKey.volumeKeyPageOnPlay, true)

    val mouseWheelPage
        get() = getPrefBoolean(PreferKey.mouseWheelPage, true)

    val paddingDisplayCutouts
        get() = getPrefBoolean(PreferKey.paddingDisplayCutouts, false)

    var searchScope: String
        get() = getPrefString("searchScope") ?: ""
        set(value) {
            putPrefString("searchScope", value)
        }

    var searchGroup: String
        get() = getPrefString("searchGroup") ?: ""
        set(value) {
            putPrefString("searchGroup", value)
        }

    var pageTouchSlop: Int
        get() = getPrefInt(PreferKey.pageTouchSlop, 0)
        set(value) {
            putPrefInt(PreferKey.pageTouchSlop, value)
        }

    var pageTouchClick: Int
        get() = getPrefInt(PreferKey.pageTouchClick, 0)
        set(value) {
            putPrefInt(PreferKey.pageTouchClick, value)
        }

    val pullToToggleBookmark
        get() = getPrefBoolean(PreferKey.pullToToggleBookmark, false)

    var bookshelfSort: Int
        get() = getPrefInt(PreferKey.bookshelfSort, 0)
        set(value) {
            putPrefInt(PreferKey.bookshelfSort, value)
        }

    fun getBookSortByGroupId(groupId: Long): Int {
        return appDb.bookGroupDao.getByID(groupId)?.getRealBookSort()
            ?: bookshelfSort
    }

    private fun getPrefUserAgent(): String {
        val ua = getPrefString(PreferKey.userAgent)
        if (ua.isNullOrBlank()) {
            return "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
        }
        return ua
    }

    var bitmapCacheSize: Int
        get() = getPrefInt(PreferKey.bitmapCacheSize, 50)
        set(value) {
            putPrefInt(PreferKey.bitmapCacheSize, value)
        }

    var imageRetainNum: Int
        get() = getPrefInt(PreferKey.imageRetainNum, 0)
        set(value) {
            putPrefInt(PreferKey.imageRetainNum, value)
        }

    var showReadTitleBarAddition: Boolean
        get() = getPrefBoolean(PreferKey.showReadTitleAddition, true)
        set(value) {
            putPrefBoolean(PreferKey.showReadTitleAddition, value)
        }
    var readBarStyleFollowPage: Boolean
        get() = getPrefBoolean(PreferKey.readBarStyleFollowPage, false)
        set(value) {
            putPrefBoolean(PreferKey.readBarStyleFollowPage, value)
        }

    var sourceEditMaxLine: Int
        get() {
            val maxLine = getPrefInt(PreferKey.sourceEditMaxLine, Int.MAX_VALUE)
            if (maxLine < 10) {
                return Int.MAX_VALUE
            }
            return maxLine
        }
        set(value) {
            putPrefInt(PreferKey.sourceEditMaxLine, value)
        }

    var audioPlayUseWakeLock: Boolean
        get() = getPrefBoolean(PreferKey.audioPlayWakeLock)
        set(value) {
            putPrefBoolean(PreferKey.audioPlayWakeLock, value)
        }

    var brightnessVwPos: Boolean
        get() = getPrefBoolean(PreferKey.brightnessVwPos)
        set(value) {
            putPrefBoolean(PreferKey.brightnessVwPos, value)
        }

    fun detectClickArea() {
        if (clickActionTL * clickActionTC * clickActionTR
            * clickActionML * clickActionMC * clickActionMR
            * clickActionBL * clickActionBC * clickActionBR != 0
        ) {
            putPrefInt(PreferKey.clickActionMC, 0)
            LogUtils.e("桌面版","当前没有配置菜单区域,自动恢复中间区域为菜单.")
        }
    }

    //跳转到漫画界面不使用富文本模式
    val showMangaUi: Boolean
        get() = getPrefBoolean(PreferKey.showMangaUi, true)

    //禁用漫画缩放
    var disableMangaScale: Boolean
        get() = getPrefBoolean(PreferKey.disableMangaScale, true)
        set(value) {
            putPrefBoolean(PreferKey.disableMangaScale, value)
        }

    var disableMangaPageAnim: Boolean
        get() = getPrefBoolean(PreferKey.disableMangaPageAnim, false)
        set(value) {
            putPrefBoolean(PreferKey.disableMangaPageAnim, value)
        }

    //漫画预加载数量
    var mangaPreDownloadNum
        get() = getPrefInt(PreferKey.mangaPreDownloadNum, 10)
        set(value) {
            putPrefInt(PreferKey.mangaPreDownloadNum, value)
        }

    //点击翻页
    var disableClickScroll
        get() = getPrefBoolean(PreferKey.disableClickScroll, false)
        set(value) {
            putPrefBoolean(PreferKey.disableClickScroll, value)
        }

    //漫画滚动速度
    var mangaAutoPageSpeed
        get() = getPrefInt(PreferKey.mangaAutoPageSpeed, 3)
        set(value) {
            putPrefInt(PreferKey.mangaAutoPageSpeed, value)
        }

    //漫画页脚配置
    var mangaFooterConfig
        get() = getPrefString(PreferKey.mangaFooterConfig, "")
        set(value) {
            putPrefString(PreferKey.mangaFooterConfig, value)
        }

    //漫画水平滚动
    var enableMangaHorizontalScroll
        get() = getPrefBoolean(PreferKey.enableMangaHorizontalScroll, false)
        set(value) {
            putPrefBoolean(PreferKey.enableMangaHorizontalScroll, value)
        }

    var mangaColorFilter
        get() = getPrefString(PreferKey.mangaColorFilter, "")
        set(value) {
            putPrefString(PreferKey.mangaColorFilter, value)
        }

    //禁用漫画内标题
    var hideMangaTitle
        get() = getPrefBoolean(PreferKey.hideMangaTitle, false)
        set(value) {
            putPrefBoolean(PreferKey.hideMangaTitle, value)
        }

    //开启墨水屏模式
    var enableMangaEInk
        get() = getPrefBoolean(PreferKey.enableMangaEInk, false)
        set(value) {
            putPrefBoolean(PreferKey.enableMangaEInk, value)
        }

    var mangaEInkThreshold
        get() = getPrefInt(PreferKey.mangaEInkThreshold, 150)
        set(value) {
            putPrefInt(PreferKey.mangaEInkThreshold, value)
        }

    var disableHorizontalPageSnap
        get() = getPrefBoolean(PreferKey.disableHorizontalPageSnap, false)
        set(value) {
            putPrefBoolean(PreferKey.disableHorizontalPageSnap, value)
        }

    var enableMangaGray
        get() = getPrefBoolean(PreferKey.enableMangaGray, false)
        set(value) {
            putPrefBoolean(PreferKey.enableMangaGray, value)
        }

    var welcomeImage
        get() = getPrefString(PreferKey.welcomeImage)
        set(value) {
            putPrefString(PreferKey.welcomeImage, value)
        }

    var welcomeShowText
        get() = getPrefBoolean(PreferKey.welcomeShowText, true)
        set(value) {
            putPrefBoolean(PreferKey.welcomeShowText, value)
        }

    var welcomeShowIcon
        get() = getPrefBoolean(PreferKey.welcomeShowIcon, true)
        set(value) {
            putPrefBoolean(PreferKey.welcomeShowIcon, value)
        }

    var welcomeImageDark
        get() = getPrefString(PreferKey.welcomeImageDark)
        set(value) {
            putPrefString(PreferKey.welcomeImageDark, value)
        }

    var welcomeShowTextDark
        get() = getPrefBoolean(PreferKey.welcomeShowTextDark, true)
        set(value) {
            putPrefBoolean(PreferKey.welcomeShowTextDark, value)
        }

    var welcomeShowIconDark
        get() = getPrefBoolean(PreferKey.welcomeShowIconDark, true)
        set(value) {
            putPrefBoolean(PreferKey.welcomeShowIconDark, value)
        }

    val autoUpdateVariant get() = getPrefBoolean("autoUpdateVariant", true)
}

internal fun normalizeJsSourceApiToken(value: String?): String? {
    return value?.trim()?.takeIf { it.isNotEmpty() }
}

