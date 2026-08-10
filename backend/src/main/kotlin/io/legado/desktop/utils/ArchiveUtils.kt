package io.legado.desktop.utils

import io.legado.desktop.constant.AppPattern.archiveFileRegex
import io.legado.desktop.env.DesktopEnv
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * 解压工具（桌面版重写：Android SAF/FileDoc → java.io）。
 * 初版支持 zip；rar/7z 后续引入 commons-compress。
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
object ArchiveUtils {

    const val TEMP_FOLDER_NAME = "ArchiveTemp"

    // 临时目录
    val TEMP_PATH: String by lazy {
        File(DesktopEnv.cacheDir.toFile(), TEMP_FOLDER_NAME).absolutePath
    }

    fun deCompress(
        archivePath: String,
        path: String = TEMP_PATH,
        filter: ((String) -> Boolean)? = null
    ): List<File> {
        return deCompress(File(archivePath), path, filter)
    }

    fun deCompress(
        archiveFile: File,
        path: String = TEMP_PATH,
        filter: ((String) -> Boolean)? = null
    ): List<File> {
        if (archiveFile.isDirectory) throw IllegalArgumentException("Unexpected Folder input")
        val name = archiveFile.name
        checkAchieve(name)
        val workPath = getCacheFolder(name, path)
        return unZip(archiveFile, File(workPath), filter)
    }

    /* 遍历目录获取文件名 */
    fun getArchiveFilesName(archivePath: String, filter: ((String) -> Boolean)? = null): List<String> {
        val file = File(archivePath)
        checkAchieve(file.name)
        return try {
            ZipFile(file).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory }
                    .map { it.name }
                    .filter { filter?.invoke(it) ?: true }
                    .toList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun isArchive(name: String): Boolean {
        return archiveFileRegex.matches(name)
    }

    private fun checkAchieve(name: String) {
        if (!isArchive(name))
            throw IllegalArgumentException("Unexpected file suffix: Only 7z rar zip Accepted")
    }

    private fun getCacheFolder(archiveName: String, workPath: String): String {
        return File(workPath, MD5Utils.md5Encode16(archiveName)).also {
            if (!it.exists()) it.mkdirs()
        }.absolutePath
    }

    /** zip 解压实现；条目路径做防目录穿越 */
    private fun unZip(zipFile: File, outDir: File, filter: ((String) -> Boolean)? = null): List<File> {
        val result = arrayListOf<File>()
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement() as ZipEntry
                val name = entry.name
                if (filter?.invoke(name) == false) continue
                val target = File(outDir, name).normalize()
                if (!target.path.startsWith(outDir.path + File.separator) && target.path != outDir.path) {
                    throw IllegalArgumentException("zip slip: $name")
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    result.add(target)
                }
            }
        }
        return result
    }
}
