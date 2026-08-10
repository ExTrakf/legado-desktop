package io.legado.desktop.utils

import io.legado.desktop.env.DesktopEnv
import java.io.File
import java.io.InputStream

/**
 * 桌面版文件工具（java.io 实现，替代 Android 版 FileUtils + SAF）。
 * 仅保留引擎常用函数。
 */
@Suppress("unused")
object FileUtils {

    fun createFileIfNotExist(root: File, vararg subDirFiles: String): File {
        val file = File(root, subDirFiles.joinToString(File.separator))
        return createFileIfNotExist(file.absolutePath)
    }

    fun createFolderIfNotExist(root: File, vararg subDirs: String): File {
        val file = File(root, subDirs.joinToString(File.separator))
        return createFolderIfNotExist(file.absolutePath)
    }

    fun createFolderIfNotExist(filePath: String): File {
        val file = File(filePath)
        if (!file.exists()) {
            file.mkdirs()
        }
        return file
    }

    fun createFileIfNotExist(filePath: String): File {
        val file = File(filePath)
        if (!file.exists()) {
            file.parentFile?.mkdirs()
            file.createNewFile()
        }
        return file
    }

    fun createFileWithReplace(filePath: String): File {
        val file = File(filePath)
        if (file.exists()) {
            file.delete()
        }
        file.parentFile?.mkdirs()
        file.createNewFile()
        return file
    }

    fun getPath(rootPath: String, vararg subDirFiles: String): String {
        val file = File(rootPath, subDirFiles.joinToString(File.separator))
        return file.absolutePath
    }

    fun getPath(root: File, vararg subDirFiles: String): String {
        val file = File(root, subDirFiles.joinToString(File.separator))
        return file.absolutePath
    }

    fun getCachePath(): String = DesktopEnv.cacheDir.toFile().absolutePath

    fun separator(path: String): String = path.replace("/", File.separator)

    fun closeSilently(c: AutoCloseable?) {
        runCatching { c?.close() }
    }

    fun listDirs(path: String, filter: ((String) -> Boolean)? = null): List<File> {
        val root = File(path)
        if (!root.exists()) return emptyList()
        return root.listFiles { f -> f.isDirectory && (filter?.invoke(f.name) ?: true) }?.toList() ?: emptyList()
    }

    fun listFiles(startDirPath: String, allowExtension: String?): Array<File>? {
        val root = File(startDirPath)
        if (!root.exists()) return null
        return root.listFiles { f ->
            f.isFile && (allowExtension == null || f.extension == allowExtension)
        }
    }

    fun listFiles(startDirPath: String, allowExtensions: Array<String>?): Array<File>? {
        val root = File(startDirPath)
        if (!root.exists()) return null
        return root.listFiles { f ->
            f.isFile && (allowExtensions == null || f.extension in allowExtensions)
        }
    }

    fun exist(path: String): Boolean = File(path).exists()

    fun delete(file: File, deleteRootDir: Boolean = false): Boolean {
        if (!file.exists()) return false
        if (file.isDirectory) {
            file.listFiles()?.forEach { delete(it, true) }
            if (deleteRootDir) file.delete() else true
        } else {
            return file.delete()
        }
        return true
    }

    fun delete(path: String, deleteRootDir: Boolean = true): Boolean {
        return delete(File(path), deleteRootDir)
    }

    fun copy(src: String, tar: String): Boolean = copy(File(src), File(tar))

    fun copy(src: File, tar: File): Boolean {
        return try {
            src.inputStream().use { input ->
                tar.outputStream().use { output -> input.copyTo(output) }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun move(src: String, tar: String): Boolean = move(File(src), File(tar))

    fun move(src: File, tar: File): Boolean {
        return try {
            if (src.exists()) {
                tar.parentFile?.mkdirs()
                src.renameTo(tar) || (copy(src, tar) && delete(src, true))
            } else false
        } catch (e: Exception) {
            false
        }
    }

    fun rename(oldPath: String, newPath: String): Boolean = move(oldPath, newPath)

    fun readText(filepath: String, charset: String = "utf-8"): String {
        return File(filepath).readText(Charsets.UTF_8)
    }

    fun readBytes(filepath: String): ByteArray? {
        return runCatching { File(filepath).readBytes() }.getOrNull()
    }

    fun writeText(filepath: String, content: String, charset: String = "utf-8"): Boolean {
        return runCatching {
            File(filepath).writeText(content, Charsets.UTF_8)
        }.isSuccess
    }

    fun writeBytes(filepath: String, data: ByteArray): Boolean {
        return runCatching { File(filepath).writeBytes(data) }.isSuccess
    }

    fun writeInputStream(filepath: String, data: InputStream): Boolean {
        return runCatching {
            File(filepath).outputStream().use { out -> data.copyTo(out) }
        }.isSuccess
    }

    fun appendText(path: String, content: String): Boolean {
        return runCatching { File(path).appendText(content, Charsets.UTF_8) }.isSuccess
    }

    fun getLength(path: String): Long = File(path).length()

    fun getName(path: String?): String {
        path ?: return ""
        return path.substringAfterLast('/').substringAfterLast('\\')
    }

    fun getNameExcludeExtension(path: String): String {
        val name = getName(path)
        return name.substringBeforeLast('.', name)
    }

    fun getSize(path: String): String {
        val length = File(path).length()
        return when {
            length < 1024 -> "${length}B"
            length < 1024 * 1024 -> "${length / 1024}KB"
            else -> "${length / 1024 / 1024}MB"
        }
    }

    fun getExtension(pathOrUrl: String): String {
        val name = getName(pathOrUrl).substringBefore('?')
        return name.substringAfterLast('.', "")
    }

    fun getMimeType(pathOrUrl: String): String {
        return when (getExtension(pathOrUrl).lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "svg" -> "image/svg+xml"
            "txt" -> "text/plain"
            "epub" -> "application/epub+zip"
            "zip" -> "application/zip"
            "mobi" -> "application/x-mobipocket-ebook"
            "pdf" -> "application/pdf"
            "html", "htm" -> "text/html"
            "json" -> "application/json"
            "mp3" -> "audio/mpeg"
            "mp4" -> "video/mp4"
            else -> "application/octet-stream"
        }
    }

    fun getDateTime(path: String, format: String = "yyyy年MM月dd日HH:mm"): String {
        val file = File(path)
        if (!file.exists()) return ""
        return java.text.SimpleDateFormat(format).format(file.lastModified())
    }
}
