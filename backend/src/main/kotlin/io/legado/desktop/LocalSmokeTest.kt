package io.legado.desktop

import io.legado.desktop.api.controller.BookController
import io.legado.desktop.data.appDb
import io.legado.desktop.data.entities.Book
import io.legado.desktop.data.entities.BookSource
import io.legado.desktop.env.DesktopEnv
import io.legado.desktop.help.book.BookHelp
import io.legado.desktop.help.config.AppConfig
import io.legado.desktop.help.source.SourceHelp
import io.legado.desktop.help.storage.Backup
import io.legado.desktop.help.storage.Restore
import io.legado.desktop.model.ImageProvider
import io.legado.desktop.model.localBook.EpubFile
import io.legado.desktop.model.localBook.LocalBook
import io.legado.desktop.model.localBook.TextFile
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/**
 * Part 6 本地书籍/封面图片/备份导入冒烟（--local-smoke-test 入口）。
 *
 * T6.1 本地书籍解析：TXT/EPUB 导入 → 目录 → 正文
 * T6.2 封面/图片接口：cover/image 返回字节
 * T6.3 备份/导入兼容：导出备份 zip → 清库 → 恢复，数据还原；Legado 备份 fixture 导入
 *
 * 全部在单进程内完成，跑完返回失败数。
 */
object LocalSmokeTest {

    private val testDir: File get() = File(DesktopEnv.homeDir.toFile(), "local-smoke")

    /** 生成测试文件并返回测试数据目录 */
    private fun prepareTestData(): File {
        val dir = testDir
        dir.deleteRecursively()
        dir.mkdirs()
        // TXT 书籍（章节正文需 >1000 字符：目录规则选择启发式以 contentLength>1000 计数章节，否则匹配被跳过）
        val txt = File(dir, "斗破苍穹.txt")
        val para =
            "萧炎，陨落的天才少年，自三年前失去斗气后沦为废柴。云岚宗上，他当着所有人的面立下三年之约。" +
                "这个曾经的天才少年，如今连斗者都不是，却依然倔强地挺直脊梁，眼神中闪烁着不屈的光芒。" +
                "他坚信，终有一天他会用实力证明自己，让所有嘲笑他的人闭嘴，重新夺回属于他的荣耀与尊严。"
        txt.writeText(
            "斗破苍穹\n作者：天蚕土豆\n\n" +
                "第一章 陨落的天才\n" + para.repeat(30) + "\n" +
                "第二章 斗气大陆\n" + para.repeat(30) + "\n" +
                "第三章 药老\n" + para.repeat(30) + "\n",
            StandardCharsets.UTF_8
        )
        // EPUB 书籍
        writeTestEpub(File(dir, "测试Epub书.epub"))
        // 封面/图片测试用 PNG
        val coverPng = File(dir, "cover-test.png")
        val img = BufferedImage(60, 80, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        g.fillRect(0, 0, 60, 80)
        g.dispose()
        ImageIO.write(img, "png", coverPng)
        return dir
    }

    /** 生成最小合法 EPUB（mimetype 首位且不压缩） */
    private fun writeTestEpub(file: File) {
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            zip.putNextEntry(ZipEntry("mimetype"))
            zip.write("application/epub+zip".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""".trimIndent().toByteArray()
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="BookId">
  <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
    <dc:title>测试Epub书</dc:title>
    <dc:creator>测试作者</dc:creator>
    <dc:description>一本用于测试的电子书</dc:description>
    <dc:identifier id="BookId">test-epub-001</dc:identifier>
  </metadata>
  <manifest>
    <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
    <item id="c1" href="chapter1.xhtml" media-type="application/xhtml+xml"/>
    <item id="cover" href="cover.jpeg" media-type="image/jpeg"/>
  </manifest>
  <spine toc="ncx">
    <itemref idref="c1"/>
  </spine>
</package>""".trimIndent().toByteArray()
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
  <head><meta name="dtb:uid" content="test-epub-001"/></head>
  <docTitle><text>测试Epub书</text></docTitle>
  <navMap>
    <navPoint id="n1" playOrder="1">
      <navLabel><text>第一章 测试开始</text></navLabel>
      <content src="chapter1.xhtml"/>
    </navPoint>
  </navMap>
</ncx>""".trimIndent().toByteArray()
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/chapter1.xhtml"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8"?>
<html xmlns="http://www.w3.org/1999/xhtml"><head><title>第一章 测试开始</title></head><body><p>这是epub第一章的内容，测试解析。</p></body></html>""".trimIndent().toByteArray()
            )
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("OEBPS/cover.jpeg"))
            val cover = BufferedImage(120, 160, BufferedImage.TYPE_INT_RGB)
            val cg = cover.createGraphics()
            cg.fillRect(0, 0, 120, 160)
            cg.dispose()
            ImageIO.write(cover, "jpeg", zip)
            zip.closeEntry()
        }
    }

    /** 返回失败数；0 = 全部通过 */
    fun run(): Int {
        var fail = 0
        fun check(name: String, block: () -> Unit) {
            try {
                block()
                println("  [PASS] $name")
            } catch (e: Throwable) {
                fail++
                println("  [FAIL] $name -> ${e.message}")
            }
        }

        val dir = prepareTestData()
        val txtPath = File(dir, "斗破苍穹.txt").absolutePath
        val epubPath = File(dir, "测试Epub书.epub").absolutePath
        val coverPngPath = File(dir, "cover-test.png").absolutePath

        try {
            // ================= T6.1 本地书籍解析 =================
            check("T6.1 LocalBook.saveBookFile 保存到书库") {
                val path = LocalBook.saveBookFile(
                    File(txtPath).inputStream(),
                    "斗破苍穹.txt"
                )
                require(File(path).exists()) { "保存后文件不存在: $path" }
            }

            check("T6.1 LocalBook.importFile 导入 TXT 入库") {
                val book = LocalBook.importFile(txtPath)
                require(appDb.bookDao.getBook(book.bookUrl) != null) { "导入后查询不到" }
                require(book.name.isNotBlank()) { "书名解析为空: name=${book.name}" }
            }

            check("T6.1 TextFile 解析 TXT 目录") {
                val book = appDb.bookDao.getBookByFileName("斗破苍穹.txt")!!
                val chapters = TextFile.getChapterList(book)
                // 原版行为：文件开头书名行被识别为前言章节，第一章起按规则分章
                val titles = chapters.map { it.title }
                require(titles.any { it.contains("第一章") }) { "缺少第一章: $titles" }
                require(titles.any { it.contains("第二章") }) { "缺少第二章: $titles" }
                require(titles.any { it.contains("第三章") }) { "缺少第三章: $titles" }
                require(titles.size >= 3) { "章节数=${chapters.size} 实际 $titles" }
            }

            check("T6.1 TextFile 读取正文") {
                val book = appDb.bookDao.getBookByFileName("斗破苍穹.txt")!!
                val chapter = TextFile.getChapterList(book).first { it.title.contains("第一章") }
                val content = TextFile.getContent(book, chapter)
                require(content.contains("萧炎")) { "正文=$content" }
            }

            check("T6.1 EpubFile 解析 EPUB 目录+正文") {
                val book = LocalBook.importFile(epubPath)
                val chapters = EpubFile.getChapterList(book)
                require(chapters.isNotEmpty()) { "epub 目录为空" }
                require(chapters[0].title.contains("第一章")) { "章节名=${chapters[0].title}" }
                val content = EpubFile.getContent(book, chapters.first())
                require(content != null && content.contains("测试解析")) { "epub 正文=$content" }
            }

            check("T6.1 LocalBook.getChapterList/getContent 分发") {
                val book = appDb.bookDao.getBookByFileName("斗破苍穹.txt")!!
                val chapters = LocalBook.getChapterList(book)
                require(chapters.isNotEmpty()) { "目录为空" }
                val chapter = chapters.first { it.title.contains("第一章") }
                val content = LocalBook.getContent(book, chapter)
                require(content?.contains("萧炎") == true) { "正文=$content" }
            }

            // ================= T6.2 封面/图片接口 =================
            check("T6.2 BookController.getCover 本地图片返回字节") {
                val data = BookController.getCover(mapOf("path" to listOf(coverPngPath)))
                require(data.isSuccess && data.data is ByteArray) {
                    "isSuccess=${data.isSuccess} data=${data.data}"
                }
                val bytes = data.data as ByteArray
                require(bytes.isNotEmpty()) { "cover 字节为空" }
            }

            check("T6.2 BookController.getImg 缓存正文图片返回字节") {
                val book = appDb.bookDao.getBookByFileName("斗破苍穹.txt")!!
                val src = "http://127.0.0.1:1/cover-test.png"
                // 预写图片到章节图片缓存（等价缓存下载完成后；下载链路由 Part2/3 网络测试覆盖）
                BookHelp.writeImage(book, src, File(coverPngPath).readBytes())
                val data = BookController.getImg(
                    mapOf(
                        "url" to listOf(book.bookUrl),
                        "path" to listOf(src),
                        "width" to listOf("60"),
                    )
                )
                require(data.isSuccess && data.data is ByteArray) {
                    "isSuccess=${data.isSuccess} data=${data.data}"
                }
                require((data.data as ByteArray).isNotEmpty()) { "img 字节为空" }
            }

            // ================= T6.3 备份/导入兼容 =================
            check("T6.3 Backup 导出备份 zip") {
                val sourceUrl = "http://127.0.0.1:1/backup-test-source"
                SourceHelp.insertBookSource(
                    BookSource(
                        bookSourceUrl = sourceUrl,
                        bookSourceName = "备份测试源",
                        enabled = true,
                    )
                )
                val backupDir = File(dir, "backup-out").also { it.mkdirs() }
                runBlocking { Backup.backup(backupDir.absolutePath) }
                val zip = File(backupDir, Backup.getNowZipFileName()).takeIf { it.exists() }
                    ?: File(backupDir, "backup.zip")
                require(zip.exists() && zip.length() > 0) { "备份 zip 不存在或为空" }
                require(appDb.bookSourceDao.getBookSource(sourceUrl) != null) { "导出前源应在库" }
            }

            check("T6.3 Restore 恢复备份还原数据") {
                val backupDir = File(dir, "backup-out")
                val zip = backupDir.listFiles()?.firstOrNull { it.name.endsWith(".zip") }
                    ?: throw IllegalStateException("备份 zip 缺失")
                // 清库（书源/书籍）
                appDb.bookSourceDao.all.forEach { appDb.bookSourceDao.delete(it) }
                require(appDb.bookSourceDao.all.isEmpty()) { "清库失败" }
                runBlocking { Restore.restoreFromFile(zip.absolutePath) }
                require(appDb.bookSourceDao.all.any { it.bookSourceName == "备份测试源" }) {
                    "恢复后书源缺失: ${appDb.bookSourceDao.all.map { it.bookSourceName }}"
                }
                require(appDb.bookDao.all.any { it.name.contains("斗破苍穹") }) {
                    "恢复后书籍缺失: ${appDb.bookDao.all.map { it.name }}"
                }
            }

            check("T6.3 手写 Legado 备份 fixture 导入") {
                val fixtureDir = File(dir, "legado-fixture").also { it.mkdirs() }
                File(fixtureDir, "bookSource.json").writeText(
                    """[{"bookSourceUrl":"https://example.com/fixture","bookSourceName":"Legado夹具源","enabled":true}]"""
                )
                File(fixtureDir, "bookshelf.json").writeText(
                    """[{"bookUrl":"https://example.com/book","name":"夹具书籍","author":"夹具作者","origin":"https://example.com/fixture","originName":"Legado夹具源","type":0}]"""
                )
                File(fixtureDir, "replaceRule.json").writeText(
                    """[{"name":"夹具替换","pattern":"测试","replacement":"TEST","isEnabled":true,"isRegex":false}]"""
                )
                val fixtureZip = File(dir, "legado-fixture.zip")
                java.util.zip.ZipOutputStream(FileOutputStream(fixtureZip)).use { zip ->
                    listOf("bookSource.json", "bookshelf.json", "replaceRule.json").forEach { name ->
                        zip.putNextEntry(ZipEntry(name))
                        zip.write(File(fixtureDir, name).readBytes())
                        zip.closeEntry()
                    }
                }
                runBlocking { Restore.restoreFromFile(fixtureZip.absolutePath) }
                require(appDb.bookSourceDao.getBookSource("https://example.com/fixture") != null) {
                    "fixture 书源未导入"
                }
                require(appDb.bookDao.getBook("https://example.com/book") != null) { "fixture 书籍未导入" }
                require(appDb.replaceRuleDao.all.any { it.name == "夹具替换" }) { "fixture 替换规则未导入" }
            }

        } finally {
            testDir.deleteRecursively()
            // 清理测试源
            runCatching {
                appDb.bookSourceDao.all
                    .filter { it.bookSourceName == "备份测试源" || it.bookSourceUrl == "https://example.com/fixture" }
                    .forEach { SourceHelp.deleteBookSource(it.bookSourceUrl) }
            }
        }
        return fail
    }
}
