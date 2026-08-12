package io.legado.desktop.model

import io.legado.desktop.constant.AppLog
import io.legado.desktop.constant.EventBus
import io.legado.desktop.data.appDb
import io.legado.desktop.data.entities.Book
import io.legado.desktop.data.entities.BookChapter
import io.legado.desktop.data.entities.BookSource
import io.legado.desktop.exception.ConcurrentException
import io.legado.desktop.help.book.BookHelp
import io.legado.desktop.help.book.isLocal
import io.legado.desktop.help.config.AppConfig
import io.legado.desktop.help.coroutine.CompositeCoroutine
import io.legado.desktop.help.coroutine.Coroutine
import io.legado.desktop.model.webBook.WebBook
import io.legado.desktop.utils.onEachParallel
import io.legado.desktop.utils.postEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

/**
 * 缓存书籍（对应原版 model/CacheBook.kt，桌面等价）。
 * 差异（文档化）：原 CacheBookService（Android 前台服务）→ 桌面协程 Job 直接驱动；
 * ReadBook 全局阅读状态未移植 → downloadAwait/downloadFinish 中相关行裁剪（无阅读 UI）。
 */
object CacheBook {

    val cacheBookMap = ConcurrentHashMap<String, CacheBookModel>()

    private val workingState = MutableStateFlow(true)
    private val mutex = Mutex()
    private var processJob: Job? = null

    @Synchronized
    fun getOrCreate(bookUrl: String): CacheBookModel? {
        val book = appDb.bookDao.getBook(bookUrl) ?: return null
        val bookSource = appDb.bookSourceDao.getBookSource(book.origin) ?: return null
        updateBookSource(bookSource)
        var cacheBook = cacheBookMap[bookUrl]
        if (cacheBook != null) {
            //存在时更新,书源可能会变化,必须更新
            cacheBook.bookSource = bookSource
            cacheBook.book = book
            return cacheBook
        }
        cacheBook = CacheBookModel(bookSource, book)
        cacheBookMap[bookUrl] = cacheBook
        return cacheBook
    }

    @Synchronized
    fun getOrCreate(bookSource: BookSource, book: Book): CacheBookModel {
        updateBookSource(bookSource)
        var cacheBook = cacheBookMap[book.bookUrl]
        if (cacheBook != null) {
            //存在时更新,书源可能会变化,必须更新
            cacheBook.bookSource = bookSource
            cacheBook.book = book
            return cacheBook
        }
        cacheBook = CacheBookModel(bookSource, book)
        cacheBookMap[book.bookUrl] = cacheBook
        return cacheBook
    }

    private fun updateBookSource(newBookSource: BookSource) {
        cacheBookMap.forEach {
            val model = it.value
            if (model.bookSource.bookSourceUrl == newBookSource.bookSourceUrl) {
                model.bookSource = newBookSource
            }
        }
    }

    /** 桌面版：直接加入缓存队列并确保处理协程运行（原 startService<CacheBookService>） */
    fun start(book: Book, start: Int, end: Int) {
        if (!book.isLocal) {
            getOrCreate(book.bookUrl)?.addDownload(start, end)
            ensureProcess()
        }
    }

    fun remove(bookUrl: String) {
        cacheBookMap[bookUrl]?.stop()
    }

    fun stop() {
        cacheBookMap.forEach { it.value.stop() }
        processJob?.cancel()
        processJob = null
    }

    fun close() {
        cacheBookMap.forEach { it.value.stop() }
        cacheBookMap.clear()
        successDownloadSet.clear()
        errorDownloadMap.clear()
    }

    fun setWorkingState(value: Boolean) {
        workingState.value = value
    }

    private fun ensureProcess() {
        if (processJob?.isActive != true) {
            processJob = CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
                startProcessJob(Dispatchers.Default)
            }
        }
    }

    suspend fun startProcessJob(context: CoroutineContext) = mutex.withLock {
        setWorkingState(true)
        flow {
            while (currentCoroutineContext().isActive && cacheBookMap.isNotEmpty()) {
                var emitted = false

                cacheBookMap.forEach { (_, model) ->
                    if (!model.isLoading()) {
                        emit(model)
                        emitted = true
                    }
                    workingState.first { it }
                }

                if (!emitted) {
                    delay(1000)
                }
            }
        }.onStart {
            postEvent(EventBus.UP_DOWNLOAD_STATE, "")
        }.onEachParallel(AppConfig.threadCount) {
            coroutineScope {
                it.download(this, context)
            }
        }.onCompletion {
            postEvent(EventBus.UP_DOWNLOAD_STATE, "")
        }.collect()
    }


    val downloadSummary: String
        get() {
            return "正在下载:${onDownloadCount}|等待中:${waitCount}|失败:${errorDownloadMap.count()}|成功:${successDownloadSet.size}"
        }

    val isRun: Boolean
        get() {
            cacheBookMap.forEach {
                if (it.value.isRun()) {
                    return true
                }
            }
            return false
        }

    private val waitCount: Int
        get() {
            var count = 0
            cacheBookMap.forEach {
                count += it.value.waitCount
            }
            return count
        }

    val onDownloadCount: Int
        get() {
            var count = 0
            cacheBookMap.forEach {
                count += it.value.onDownloadCount
            }
            return count
        }

    val successDownloadSet = linkedSetOf<String>()
    val errorDownloadMap = hashMapOf<String, Int>()

    class CacheBookModel(var bookSource: BookSource, var book: Book) {

        private val waitDownloadSet = linkedSetOf<Int>()
        private val onDownloadSet = linkedSetOf<Int>()
        private val tasks = CompositeCoroutine()
        private var isStopped = false
        private var waitingRetry = false
        private var isLoading = false

        val waitCount get() = waitDownloadSet.size
        val onDownloadCount get() = onDownloadSet.size

        init {
            postEvent(EventBus.UP_DOWNLOAD, book.bookUrl)
        }

        @Synchronized
        fun isRun(): Boolean {
            return waitDownloadSet.isNotEmpty() || onDownloadSet.isNotEmpty() || isLoading
        }

        @Synchronized
        fun isStop(): Boolean {
            return isStopped || (!isRun() && !waitingRetry)
        }

        @Synchronized
        fun isLoading(): Boolean {
            return isLoading
        }

        @Synchronized
        fun setLoading() {
            isLoading = true
        }

        @Synchronized
        fun stop() {
            waitDownloadSet.clear()
            tasks.clear()
            isStopped = true
            isLoading = false
            postEvent(EventBus.UP_DOWNLOAD, book.bookUrl)
        }

        @Synchronized
        fun addDownload(start: Int, end: Int) {
            isStopped = false
            for (i in start..end) {
                if (!onDownloadSet.contains(i)) {
                    waitDownloadSet.add(i)
                }
            }
            cacheBookMap[book.bookUrl] = this
            isLoading = false
            postEvent(EventBus.UP_DOWNLOAD, book.bookUrl)
        }

        @Synchronized
        private fun onSuccess(chapter: BookChapter) {
            onDownloadSet.remove(chapter.index)
            successDownloadSet.add(chapter.primaryStr())
            errorDownloadMap.remove(chapter.primaryStr())
        }

        @Synchronized
        private fun onPreError(chapter: BookChapter, error: Throwable) {
            waitingRetry = true
            if (error !is ConcurrentException) {
                errorDownloadMap[chapter.primaryStr()] =
                    (errorDownloadMap[chapter.primaryStr()] ?: 0) + 1
            }
            onDownloadSet.remove(chapter.index)
        }

        @Synchronized
        private fun onPostError(chapter: BookChapter, error: Throwable) {
            //重试3次
            if ((errorDownloadMap[chapter.primaryStr()] ?: 0) < 3 && !isStopped) {
                waitDownloadSet.add(chapter.index)
            } else {
                AppLog.put(
                    "下载${book.name}-${chapter.title}失败\n${error.localizedMessage}",
                    error
                )
            }
            waitingRetry = false
        }

        @Synchronized
        private fun onReadError(chapter: BookChapter, error: Throwable) {
            if (error !is ConcurrentException) {
                errorDownloadMap[chapter.primaryStr()] =
                    (errorDownloadMap[chapter.primaryStr()] ?: 0) + 1
            }
            onDownloadSet.remove(chapter.index)
        }

        @Synchronized
        private fun onCancel(index: Int) {
            onDownloadSet.remove(index)
            if (!isStopped) waitDownloadSet.add(index)
        }

        @Synchronized
        private fun onReadCancel(index: Int) {
            onDownloadSet.remove(index)
        }

        @Synchronized
        private fun onFinally() {
            if (waitDownloadSet.isEmpty() && onDownloadSet.isEmpty()) {
                cacheBookMap.remove(book.bookUrl)
            }
            postEvent(EventBus.UP_DOWNLOAD, book.bookUrl)
        }

        /**
         * 从待下载列表内取第一条下载
         */
        @Synchronized
        fun download(scope: CoroutineScope, context: CoroutineContext) {
            val chapterIndex = waitDownloadSet.firstOrNull()
            if (chapterIndex == null) {
                if (!isLoading && onDownloadSet.isEmpty()) {
                    cacheBookMap.remove(book.bookUrl)
                }
                return
            }
            if (onDownloadSet.contains(chapterIndex)) {
                waitDownloadSet.remove(chapterIndex)
                return
            }
            val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, chapterIndex) ?: let {
                waitDownloadSet.remove(chapterIndex)
                return
            }
            if (chapter.isVolume) {
                /** 修正下载计数 */
                postEvent(EventBus.SAVE_CONTENT, Pair(book, chapter))
                waitDownloadSet.remove(chapterIndex)
                return
            }
            if (BookHelp.hasImageContent(book, chapter)) {
                waitDownloadSet.remove(chapterIndex)
                return
            }
            waitDownloadSet.remove(chapterIndex)
            onDownloadSet.add(chapterIndex)
            if (BookHelp.hasContent(book, chapter)) {
                Coroutine.async(scope, context, executeContext = context) {
                    BookHelp.getContent(book, chapter)?.let {
                        BookHelp.saveImages(bookSource, book, chapter, it, 1)
                    }
                }.onSuccess {
                    onSuccess(chapter)
                }.onError {
                    onPreError(chapter, it)
                    //出现错误等待一秒后重新加入待下载列表
                    delay(1000)
                    onPostError(chapter, it)
                }.onCancel {
                    onCancel(chapterIndex)
                }.onFinally {
                    onFinally()
                }.let {
                    tasks.add(it)
                }
                return
            }
            WebBook.getContent(
                scope,
                bookSource,
                book,
                chapter,
                context = context,
                start = CoroutineStart.LAZY,
                executeContext = context
            ).onSuccess { content ->
                val imageContent = BookHelp.getContent(book, chapter) ?: content
                BookHelp.saveImages(bookSource, book, chapter, imageContent, 1)
                val currentContent = BookHelp.getContent(book, chapter) ?: imageContent
                onSuccess(chapter)
                downloadFinish(chapter, currentContent)
            }.onError {
                onPreError(chapter, it)
                //出现错误等待一秒后重新加入待下载列表
                delay(1000)
                onPostError(chapter, it)
                downloadFinish(chapter, "获取正文失败\n${it.localizedMessage}")
            }.onCancel {
                onCancel(chapterIndex)
            }.onFinally {
                onFinally()
            }.apply {
                tasks.add(this)
            }.start()
        }

        suspend fun downloadAwait(chapter: BookChapter): String {
            synchronized(this) {
                onDownloadSet.add(chapter.index)
                waitDownloadSet.remove(chapter.index)
            }
            try {
                val content = WebBook.getContentAwait(bookSource, book, chapter)
                val currentContent = BookHelp.getContent(book, chapter) ?: content
                onSuccess(chapter)
                return currentContent
            } catch (e: CancellationException) {
                onReadCancel(chapter.index)
                throw e
            } catch (e: Exception) {
                onReadError(chapter, e)
                return "获取正文失败\n${e.localizedMessage}"
            } finally {
                postEvent(EventBus.UP_DOWNLOAD, book.bookUrl)
            }
        }

        @Synchronized
        fun download(
            scope: CoroutineScope,
            chapter: BookChapter,
            semaphore: Semaphore?,
            resetPageOffset: Boolean = false
        ) {
            if (onDownloadSet.contains(chapter.index)) {
                return
            }
            onDownloadSet.add(chapter.index)
            waitDownloadSet.remove(chapter.index)
            WebBook.getContent(
                scope,
                bookSource,
                book,
                chapter,
                start = CoroutineStart.LAZY,
                executeContext = Dispatchers.IO,
                semaphore = semaphore
            ).onSuccess { content ->
                val currentContent = BookHelp.getContent(book, chapter) ?: content
                onSuccess(chapter)
                downloadFinish(chapter, currentContent, resetPageOffset)
            }.onError {
                onReadError(chapter, it)
                downloadFinish(chapter, "获取正文失败\n${it.localizedMessage}", resetPageOffset)
            }.onCancel {
                onReadCancel(chapter.index)
                downloadFinish(chapter, "download canceled", resetPageOffset, true)
            }.onFinally {
                postEvent(EventBus.UP_DOWNLOAD, book.bookUrl)
            }.start()
        }

        private fun downloadFinish(
            chapter: BookChapter,
            content: String,
            resetPageOffset: Boolean = false,
            canceled: Boolean = false
        ) {
            // 桌面版无 ReadBook 全局阅读状态（原 ReadBook.contentLoadFinish 裁剪）
        }

    }

}
