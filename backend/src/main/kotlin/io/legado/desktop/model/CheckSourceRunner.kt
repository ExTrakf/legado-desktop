package io.legado.desktop.model

import com.script.ScriptException
import io.legado.desktop.constant.AppConst
import io.legado.desktop.constant.BookSourceType
import io.legado.desktop.constant.EventBus
import io.legado.desktop.data.appDb
import io.legado.desktop.data.entities.Book
import io.legado.desktop.data.entities.BookChapter
import io.legado.desktop.data.entities.BookSource
import io.legado.desktop.data.entities.BookSourcePart
import io.legado.desktop.exception.ContentEmptyException
import io.legado.desktop.exception.NoStackTraceException
import io.legado.desktop.exception.TocEmptyException
import io.legado.desktop.help.config.AppConfig
import io.legado.desktop.help.source.exploreKinds
import io.legado.desktop.model.webBook.WebBook
import io.legado.desktop.utils.LogUtils
import io.legado.desktop.utils.mapParallel
import io.legado.desktop.utils.postEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.htmlunit.corejs.javascript.WrappedException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.Executors
import kotlin.math.min

/** 等价迁移自 io.legado.app.service.CheckSourceService 的顶层函数（原版逐字） */
internal fun parseCheckSourceEndpoint(domain: String): Pair<String, Int>? {
    val rawUrl = domain.substringBefore('#')
    val uri = kotlin.runCatching { URI(rawUrl) }.getOrNull() ?: return null
    if (uri.rawAuthority.isNullOrBlank()) return null
    val url = rawUrl.toHttpUrlOrNull() ?: return null
    return url.host to url.port
}

/** 等价迁移自 io.legado.app.service.CheckSourceChapterSelection（原版逐字） */
internal data class CheckSourceChapterSelection(
    val chapter: BookChapter,
    val nextChapterUrl: String,
)

/** 等价迁移自 io.legado.app.service.CheckSourceChapterSelection.selectCheckSourceChapter（原版逐字） */
internal fun selectCheckSourceChapter(
    chapters: List<BookChapter>,
    emptyMessage: String,
): CheckSourceChapterSelection {
    val readableChapters = chapters.asSequence()
        .filterNot { it.isVolume && it.url.startsWith(it.title) }
        .take(2)
        .toList()
    val chapter = readableChapters.firstOrNull()
        ?: throw TocEmptyException(emptyMessage)
    return CheckSourceChapterSelection(
        chapter = chapter,
        nextChapterUrl = readableChapters.getOrNull(1)?.url ?: chapter.url,
    )
}

/**
 * 桌面版书源校验执行器 —— 等价迁移自 io.legado.app.service.CheckSourceService（Android Service）。
 *
 * 业务逻辑（check / checkSource / doCheckSource / checkBook / isDomainReachable）逐字保留；
 * 裁剪：BaseService / Notification / Intent / 生命周期（start/stop/resume 由 CheckSource 直接驱动）。
 * 通知与进度 → 日志输出 + postEvent（EventBus 保留调用点）。
 * appCtx.getString(R.string.xxx) → 硬编码中文（对照 values-zh/strings.xml）。
 */
object CheckSourceRunner {

    private data class CheckTarget(
        val selected: BookSourcePart,
        val original: BookSource,
        val source: BookSource,
    )

    private data class CheckOutcome(
        val succeeded: Boolean,
        val message: String,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var checkJob: Job? = null

    /** 当前活动校验会话（原 CheckSourceService.checkSessionId）；null = 无活动校验 */
    @Volatile
    var activeSessionId: Long? = null
        private set

    /** 原 CheckSourceService.onStartCommand(IntentAction.start) 的等价入口 */
    @Synchronized
    fun start(selectedSources: List<BookSourcePart>, sessionId: Long) {
        if (checkJob?.isActive == true) {
            // 原版 toastOnUi("已有书源在校验,等完成后再试") 后直接返回，桌面版记日志等价
            LogUtils.d("CheckSource", "已有书源在校验,等完成后再试")
            return
        }
        if (!Debug.markCheckServiceStarted(sessionId)) {
            return
        }
        activeSessionId = sessionId
        val threadCount = AppConfig.threadCount
        val searchCoroutine =
            Executors.newFixedThreadPool(min(threadCount, AppConst.MAX_THREAD)).asCoroutineDispatcher()
        var originSize = 0
        var finishCount = 0
        val job = scope.launch(searchCoroutine) {
            flow {
                for (selected in selectedSources) {
                    val source = appDb.bookSourceDao.getBookSource(selected.bookSourceUrl)
                    when {
                        source == null -> Debug.recordCheckResult(
                            sessionId,
                            selected.bookSourceUrl,
                            CheckSourceResult(
                                CheckSourceStatus.NOT_COMPLETED,
                                "书源已删除",
                            ),
                        )

                        source.lastUpdateTime != selected.lastUpdateTime ->
                            Debug.recordCheckResult(
                                sessionId,
                                selected.bookSourceUrl,
                                CheckSourceResult(
                                    CheckSourceStatus.NOT_COMPLETED,
                                    "书源已变更，校验结果未写回",
                                ),
                            )

                        else -> emit(CheckTarget(selected, source.copy(), source))
                    }
                }
            }.onStart {
                originSize = selectedSources.size
                finishCount = 0
                upNotification(sessionId, "", 0, originSize)
            }.mapParallel(threadCount) {
                it to checkSource(it.source, sessionId)
            }.onEach { (target, outcome) ->
                val (selected, original, source) = target
                finishCount++
                upNotification(sessionId, source.bookSourceName, finishCount, originSize)
                val updated = appDb.bookSourceDao.updateCheckResult(
                    source.bookSourceUrl,
                    source.bookSourceGroup,
                    source.bookSourceComment,
                    source.respondTime,
                    selected.lastUpdateTime,
                    original.bookSourceGroup,
                    original.bookSourceComment,
                    original.respondTime,
                )
                if (updated == 0) {
                    val detail = "校验结果未写回：书源已变更或删除"
                    Debug.updateCheckMessage(
                        sessionId,
                        source.bookSourceUrl,
                        detail,
                    )
                    Debug.recordCheckResult(
                        sessionId,
                        source.bookSourceUrl,
                        CheckSourceResult(CheckSourceStatus.NOT_COMPLETED, detail),
                    )
                } else {
                    Debug.updateFinalMessage(sessionId, source.bookSourceUrl, outcome.message)
                    val status = if (outcome.succeeded) {
                        CheckSourceStatus.PASSED
                    } else {
                        CheckSourceStatus.FAILED
                    }
                    val detail = if (outcome.succeeded) {
                        ""
                    } else {
                        listOf(
                            source.getInvalidGroupNames(),
                            source.bookSourceComment
                                ?.lineSequence()
                                ?.firstOrNull { it.startsWith("// Error: ") }
                                .orEmpty(),
                            outcome.message,
                        ).filter { it.isNotEmpty() }.distinct().joinToString(" | ")
                    }
                    Debug.recordCheckResult(
                        sessionId,
                        source.bookSourceUrl,
                        CheckSourceResult(status, detail),
                    )
                }
            }.collect()
        }
        checkJob = job
        job.invokeOnCompletion {
            searchCoroutine.close()
            if (activeSessionId == sessionId) activeSessionId = null
            // 原 CheckSourceService.onDestroy/onStartCommand 完成路径：finishCheckSession
            if (Debug.finishChecking(sessionId)) {
                postEvent(EventBus.CHECK_SOURCE_DONE, sessionId)
            }
        }
    }

    /** 原 CheckSourceService.onStartCommand(IntentAction.stop) 的等价入口 */
    fun cancel(sessionId: Long) {
        if (activeSessionId == null) {
            if (Debug.isChecking(sessionId)) {
                Debug.finishChecking(sessionId)
            }
        } else {
            checkJob?.cancel()
        }
    }

    /** 原 CheckSourceService.upNotification —— 桌面版无通知，输出日志并保留 EventBus 调用点 */
    private fun upNotification(sessionId: Long, msg: String, finish: Int, total: Int) {
        val progressMsg = if (total > 0) "%s      进度 %d/%d".format(msg, finish, total) else "正在启动服务"
        LogUtils.d("CheckSource", progressMsg)
        postEvent(EventBus.CHECK_SOURCE, sessionId to progressMsg)
    }

    /** 原 CheckSourceService.checkSource（逐字保留） */
    private suspend fun checkSource(source: BookSource, sessionId: Long): CheckOutcome {
        var resultMessage = "校验成功"
        var succeeded = true
        kotlin.runCatching {
            withTimeout(CheckSource.timeout) {
                doCheckSource(source, sessionId)
            }
        }.onFailure {
            currentCoroutineContext().ensureActive()
            succeeded = false
            when (it) {
                is TimeoutCancellationException -> source.addGroup("校验超时")
                is ScriptException, is WrappedException -> source.addGroup("js失效")
                !is NoStackTraceException -> source.addGroup("网站失效")
            }
            if (CheckSource.wSourceComment) {
                source.addErrorComment(it)
            }
            resultMessage = "校验失败:${it.localizedMessage}"
        }
        source.respondTime = Debug.getRespondTime(sessionId, source.bookSourceUrl, succeeded)
        return CheckOutcome(succeeded, resultMessage)
    }

    /** 原 CheckSourceService.isDomainReachable（逐字保留） */
    private suspend fun isDomainReachable(endpoint: Pair<String, Int>): Boolean {
        return kotlin.runCatching {
            withTimeout(2000) {
                val (host, port) = endpoint
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), 1600)
                    true
                }
            }
        }.getOrDefault(false)
    }

    /** 原 CheckSourceService.doCheckSource（逐字保留） */
    private suspend fun doCheckSource(source: BookSource, sessionId: Long) {
        Debug.startChecking(sessionId, source)
        source.removeInvalidGroups()
        if (CheckSource.wSourceComment) {
            source.removeErrorComment()
        }
        //检测源地址可访问性
        if (CheckSource.checkDomain) {
            val domain = source.bookSourceUrl
            val endpoint = parseCheckSourceEndpoint(domain)
            if (endpoint == null) {
                throw NoStackTraceException("源地址不是http链接")
            } else if (isDomainReachable(endpoint)) {
                source.removeGroup("域名失效")
            } else {
                source.addGroup("域名失效")
                throw NoStackTraceException("源地址不可访问")
            }
        }
        //校验搜索书籍
        if (CheckSource.checkSearch) {
            val searchWord = source.getCheckKeyword(CheckSource.keyword)
            if (source.isJsSource() || !source.searchUrl.isNullOrBlank()) {
                source.removeGroup("搜索链接规则为空")
                val searchBooks = WebBook.searchBookAwait(source, searchWord)
                if (searchBooks.isEmpty()) {
                    source.addGroup("搜索失效")
                } else {
                    source.removeGroup("搜索失效")
                    checkBook(searchBooks.first().toBook(), source)
                }
            } else {
                source.addGroup("搜索链接规则为空")
            }
        }
        //校验发现书籍
        if (CheckSource.checkDiscovery && !source.exploreUrl.isNullOrBlank()) {
            val url = source.exploreKinds().firstOrNull {
                !it.url.isNullOrBlank()
            }?.url
            if (url.isNullOrBlank()) {
                source.addGroup("发现规则为空")
            } else {
                source.removeGroup("发现规则为空")
                val exploreBooks = WebBook.exploreBookAwait(source, url)
                if (exploreBooks.isEmpty()) {
                    source.addGroup("发现失效")
                } else {
                    source.removeGroup("发现失效")
                    checkBook(exploreBooks.first().toBook(), source, false)
                }
            }
        }
        val finalCheckMessage = source.getInvalidGroupNames()
        if (finalCheckMessage.isNotBlank()) {
            throw NoStackTraceException(finalCheckMessage)
        }
    }

    /**
     * 校验书源的详情目录正文（原 CheckSourceService.checkBook，逐字保留）
     */
    private suspend fun checkBook(book: Book, source: BookSource, isSearchBook: Boolean = true) {
        kotlin.runCatching {
            if (!CheckSource.checkInfo) {
                return
            }
            //校验详情
            if (book.tocUrl.isBlank()) {
                WebBook.getBookInfoAwait(source, book)
            }
            if (!CheckSource.checkCategory || source.bookSourceType == BookSourceType.file) {
                return
            }
            //校验目录
            val chapterSelection = selectCheckSourceChapter(
                chapters = WebBook.getChapterListAwait(source, book).getOrThrow(),
                emptyMessage = "目录列表为空",
            )
            if (!CheckSource.checkContent) {
                return
            }
            //校验正文
            WebBook.getContentAwait(
                bookSource = source,
                book = book,
                bookChapter = chapterSelection.chapter,
                nextChapterUrl = chapterSelection.nextChapterUrl,
                needSave = false
            )
        }.onFailure {
            val bookType = if (isSearchBook) "搜索" else "发现"
            when (it) {
                is ContentEmptyException -> source.addGroup("${bookType}正文失效")
                is TocEmptyException -> source.addGroup("${bookType}目录失效")
                else -> throw it
            }
        }.onSuccess {
            val bookType = if (isSearchBook) "搜索" else "发现"
            source.removeGroup("${bookType}目录失效")
            source.removeGroup("${bookType}正文失效")
        }
    }
}
