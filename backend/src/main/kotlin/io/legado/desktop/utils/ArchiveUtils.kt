package io.legado.desktop.utils

import com.github.junrar.Archive
import io.legado.desktop.constant.AppPattern.archiveFileRegex
import io.legado.desktop.env.DesktopEnv
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * 解压工具（桌面版重写：Android SAF/FileDoc/libarchive-JNI → java.io/commons-compress/junrar）。
 * zip 用 JDK；7z 用 commons-compress；rar 用 junrar（RAR3）。
 * 条目路径统一做防目录穿越（zip slip）。
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
        val outDir = File(workPath)
        return when {
            name.endsWith(".7z", true) -> un7z(archiveFile, outDir, filter)
            name.endsWith(".rar", true) -> unRar(archiveFile, outDir, filter)
            else -> unZip(archiveFile, outDir, filter)
        }
    }

    /* 遍历目录获取文件名 */
    fun getArchiveFilesName(archivePath: String, filter: ((String) -> Boolean)? = null): List<String> {
        val file = File(archivePath)
        checkAchieve(file.name)
        return try {
            when {
                file.name.endsWith(".7z", true) -> sevenZEntryNames(file, filter)
                file.name.endsWith(".rar", true) -> rarEntryNames(file, filter)
                else -> zipEntryNames(file, filter)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 从 7z 压缩包按路径读取单个文件字节（对应原 LibArchiveUtils 7z 读取） */
    fun read7zEntryBytes(bytes: ByteArray, path: String): ByteArray? {
        val temp = File.createTempFile("legado-7z", ".7z")
        return try {
            temp.writeBytes(bytes)
            SevenZFile(temp).use { sevenZ ->
                var entry = sevenZ.nextEntry
                while (entry != null) {
                    if (entry.name == path) {
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(8192)
                        var read = sevenZ.read(buf)
                        while (read != -1) {
                            out.write(buf, 0, read)
                            read = sevenZ.read(buf)
                        }
                        return@use out.toByteArray()
                    }
                    entry = sevenZ.nextEntry
                }
                null
            }
        } catch (_: Exception) {
            null
        } finally {
            temp.delete()
        }
    }

    /** 从 rar 压缩包按路径读取单个文件字节（对应原 LibArchiveUtils rar 读取） */
    fun readRarEntryBytes(bytes: ByteArray, path: String): ByteArray? {
        return try {
            Archive(ByteArrayInputStream(bytes)).use { archive ->
                var header = archive.nextFileHeader()
                while (header != null) {
                    if (header.fileNameString == path) {
                        return@use archive.getInputStream(header).use { it.readBytes() }
                    }
                    header = archive.nextFileHeader()
                }
                null
            }
        } catch (_: Exception) {
            null
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

    /** zip-slip 防护：条目路径规范化后必须落在 outDir 内 */
    private fun safeResolve(outDir: File, name: String): File {
        val target = File(outDir, name).normalize()
        if (!target.path.startsWith(outDir.path + File.separator) && target.path != outDir.path) {
            throw IllegalArgumentException("zip slip: $name")
        }
        return target
    }

    /** zip 解压实现 */
    private fun unZip(zipFile: File, outDir: File, filter: ((String) -> Boolean)? = null): List<File> {
        val result = arrayListOf<File>()
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement() as ZipEntry
                val name = entry.name
                if (filter?.invoke(name) == false) continue
                val target = safeResolve(outDir, name)
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

    /** 7z 解压（commons-compress） */
    private fun un7z(archiveFile: File, outDir: File, filter: ((String) -> Boolean)? = null): List<File> {
        val result = arrayListOf<File>()
        SevenZFile(archiveFile).use { sevenZ ->
            val buf = ByteArray(8192)
            var entry = sevenZ.nextEntry
            while (entry != null) {
                val name = entry.name
                if (filter?.invoke(name) != false) {
                    val target = safeResolve(outDir, name)
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        FileOutputStream(target).use { out ->
                            var read = sevenZ.read(buf)
                            while (read != -1) {
                                out.write(buf, 0, read)
                                read = sevenZ.read(buf)
                            }
                        }
                        result.add(target)
                    }
                }
                entry = sevenZ.nextEntry
            }
        }
        return result
    }

    /** rar 解压（junrar，RAR3） */
    private fun unRar(archiveFile: File, outDir: File, filter: ((String) -> Boolean)? = null): List<File> {
        val result = arrayListOf<File>()
        Archive(FileInputStream(archiveFile)).use { archive ->
            var header = archive.nextFileHeader()
            while (header != null) {
                val name = header.fileName
                if (filter?.invoke(name) != false) {
                    val target = safeResolve(outDir, name)
                    if (header.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        archive.getInputStream(header).use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                        result.add(target)
                    }
                }
                header = archive.nextFileHeader()
            }
        }
        return result
    }

    private fun zipEntryNames(file: File, filter: ((String) -> Boolean)?): List<String> {
        ZipFile(file).use { zip ->
            return zip.entries().asSequence()
                .filter { !it.isDirectory }
                .map { it.name }
                .filter { filter?.invoke(it) ?: true }
                .toList()
        }
    }

    private fun sevenZEntryNames(file: File, filter: ((String) -> Boolean)?): List<String> {
        SevenZFile(file).use { sevenZ ->
            val names = arrayListOf<String>()
            var entry = sevenZ.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && (filter?.invoke(entry.name) ?: true)) {
                    names.add(entry.name)
                }
                entry = sevenZ.nextEntry
            }
            return names
        }
    }

    private fun rarEntryNames(file: File, filter: ((String) -> Boolean)?): List<String> {
        Archive(FileInputStream(file)).use { archive ->
            val names = arrayListOf<String>()
            var header = archive.nextFileHeader()
            while (header != null) {
                if (!header.isDirectory && (filter?.invoke(header.fileName) ?: true)) {
                    names.add(header.fileName)
                }
                header = archive.nextFileHeader()
            }
            return names
        }
    }
}
