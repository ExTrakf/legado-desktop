package io.legado.desktop.help

import io.legado.desktop.env.DesktopEnv
import io.legado.desktop.exception.NoStackTraceException
import io.legado.desktop.model.analyzeRule.AnalyzeRule
import io.legado.desktop.model.analyzeRule.AnalyzeRule.Companion.setCoroutineContext
import io.legado.desktop.model.analyzeRule.AnalyzeUrl
import io.legado.desktop.utils.FileUtils
import io.legado.desktop.utils.GSON
import io.legado.desktop.utils.compress.ZipUtils
import io.legado.desktop.utils.createFileReplace
import io.legado.desktop.utils.fromJsonArray
import io.legado.desktop.utils.fromJsonObject
import kotlinx.coroutines.currentCoroutineContext
import java.io.File

@Suppress("MemberVisibilityCanBePrivate")
object DirectLinkUpload {

    const val ruleFileName = "directLinkUploadRule.json"

    @Throws(NoStackTraceException::class)
    suspend fun upLoad(
        fileName: String,
        file: Any,
        contentType: String,
        rule: Rule = getRule()
    ): String {
        val url = rule.uploadUrl
        if (url.isBlank()) {
            throw NoStackTraceException("上传url未配置")
        }
        val downloadUrlRule = rule.downloadUrlRule
        if (downloadUrlRule.isBlank()) {
            throw NoStackTraceException("下载地址规则未配置")
        }
        var mFileName = fileName
        var mFile = file
        var mContentType = contentType
        if (rule.compress && contentType != "application/zip") {
            mFileName = "$fileName.zip"
            mContentType = "application/zip"
            mFile = when (file) {
                is File -> {
                    val zipFile = File(FileUtils.getPath(DesktopEnv.cacheDir.toFile(), "upload", mFileName))
                    zipFile.createFileReplace()
                    ZipUtils.zipFile(file, zipFile)
                    zipFile
                }

                is ByteArray -> ZipUtils.zipByteArray(file, fileName)
                is String -> ZipUtils.zipByteArray(file.toByteArray(), fileName)
                else -> ZipUtils.zipByteArray(GSON.toJson(file).toByteArray(), fileName)
            }
        }
        val analyzeUrl = AnalyzeUrl(url)
        val res = analyzeUrl.upload(mFileName, mFile, mContentType)
        if (mFile is File) {
            mFile.delete()
        }
        val analyzeRule = AnalyzeRule().setContent(res.body, res.url)
            .setCoroutineContext(currentCoroutineContext())
        val downloadUrl = analyzeRule.getString(downloadUrlRule)
        if (downloadUrl.isBlank()) {
            throw NoStackTraceException("上传失败,${res.body}")
        }
        return downloadUrl
    }

    val defaultRules: List<Rule> by lazy {
        val json = String(
            javaClass.getResourceAsStream("/defaultData/directLinkUpload.json")
                .readBytes()
        )
        GSON.fromJsonArray<Rule>(json).getOrThrow()
    }

    fun getRule(): Rule {
        return getConfig() ?: defaultRules[0]
    }

    fun getConfig(): Rule? {
        val json = SimpleACache.getAsString(ruleFileName)
        return GSON.fromJsonObject<Rule>(json).getOrNull()
    }

    fun putConfig(rule: Rule) {
        SimpleACache.put(ruleFileName, GSON.toJson(rule))
    }

    fun delConfig() {
        SimpleACache.remove(ruleFileName)
    }

    fun getSummary(): String {
        return getRule().summary
    }

    fun getExpiryDate(): Int {
        return getRule().expiryDate
    }

        data class Rule(
        var uploadUrl: String, //创建分享链接
        var downloadUrlRule: String, //下载链接规则
        var summary: String, //注释
        var compress: Boolean = false, //是否压缩
        var expiryDate: Int = 0, //有效期/天，0为永久
    ) {

        override fun toString(): String {
            return summary
        }

    }

}
