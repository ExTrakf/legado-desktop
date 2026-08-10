@file:Suppress("unused")

package io.legado.desktop.utils

import io.legado.desktop.constant.AppConst
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

/**
 * 日志（桌面版）：java.util.logging + 可选文件输出，替代 Android Log。
 */
@Suppress("unused")
object LogUtils {
    const val TIME_PATTERN = "yy-MM-dd HH:mm:ss.SSS"
    val logTimeFormat by lazy { SimpleDateFormat(TIME_PATTERN) }

    @JvmStatic
    fun d(tag: String, msg: String) {
        logger.log(Level.INFO, "$tag $msg")
        println("[$tag] $msg")
    }

    inline fun d(tag: String, lazyMsg: () -> String) {
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "$tag ${lazyMsg()}")
        }
    }

    @JvmStatic
    fun e(tag: String, msg: String) {
        logger.log(Level.WARNING, "$tag $msg")
        System.err.println("[$tag] $msg")
    }

    val logger: Logger by lazy {
        Logger.getLogger("Legado")
    }

    private var fileHandler: FileHandler? = null

    /** 初始化文件日志，需要 DesktopEnv 初始化后调用 */
    fun initFileLog(dir: Path) {
        try {
            Files.createDirectories(dir)
            fileHandler = createFileHandler(dir)?.also {
                logger.addHandler(it)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun createFileHandler(dir: Path): FileHandler? {
        return try {
            val date = getCurrentDateStr(TIME_PATTERN).replace(" ", "_").replace(":", "-")
            val logPath = dir.resolve("appLog-$date.txt").toString()
            FileHandler(logPath).apply {
                formatter = object : java.util.logging.Formatter() {
                    override fun format(record: LogRecord): String {
                        return getCurrentDateStr(TIME_PATTERN) + ": " + record.message + "\n"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** 获取当前时间 */
    fun getCurrentDateStr(pattern: String): String {
        return SimpleDateFormat(pattern).format(Date())
    }

    fun logEnvInfo() {
        d("EnvInfo") {
            buildString {
                append("os.name=").append(System.getProperty("os.name")).append("\n")
                append("os.arch=").append(System.getProperty("os.arch")).append("\n")
                append("java.version=").append(System.getProperty("java.version")).append("\n")
                append("heapSize=").append(Runtime.getRuntime().maxMemory()).append("\n")
                append("version=").append(AppConst.VERSION).append("\n")
            }
        }
    }
}

fun Throwable.printOnDebug() {
    printStackTrace()
}
