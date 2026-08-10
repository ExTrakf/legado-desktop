package io.legado.desktop.utils

import java.util.Base64

/**
 * 编码工具 escape base64（桌面版）。
 *
 * 与 Android util.Base64 的 flags 语义保持兼容：
 * - DEFAULT = 0       标准编码（RFC4648，带 padding，无换行差异按 java 默认）
 * - NO_PADDING = 1    不带 padding
 * - NO_WRAP = 2       不换行（java.util.Base64 默认即不换行）
 * - CRLF = 4          换行用 CRLF（java MIME 解码器可容忍）
 * - URL_SAFE = 8      用 - _ 替代 + /
 * 解码端对 URL_SAFE / MIME（允许换行、无 padding）均做兼容。
 */
@Suppress("unused")
object EncoderUtils {

    private const val NO_PADDING = 1
    private const val NO_WRAP = 2
    private const val CRLF = 4
    private const val URL_SAFE = 8

    fun escape(src: String): String {
        val tmp = StringBuilder()
        for (char in src) {
            val charCode = char.code
            if (charCode in 48..57 || charCode in 65..90 || charCode in 97..122) {
                tmp.append(char)
                continue
            }

            val prefix = when {
                charCode < 16 -> "%0"
                charCode < 256 -> "%"
                else -> "%u"
            }
            tmp.append(prefix).append(charCode.toString(16))
        }
        return tmp.toString()
    }

    @JvmOverloads
    fun base64Decode(str: String, flags: Int = 0): String {
        val bytes = base64DecodeToByteArray(str, flags)
        return String(bytes)
    }

    @JvmOverloads
    fun base64Encode(str: String, flags: Int = NO_WRAP): String? {
        return base64Encode(str.toByteArray(), flags)
    }

    @JvmOverloads
    fun base64Encode(bytes: ByteArray, flags: Int = NO_WRAP): String {
        val encoder = if (flags and URL_SAFE != 0) {
            Base64.getUrlEncoder()
        } else {
            Base64.getEncoder()
        }
        val withPadding = flags and NO_PADDING == 0
        return if (withPadding) encoder.encodeToString(bytes) else encoder.withoutPadding().encodeToString(bytes)
    }

    @JvmOverloads
    fun base64DecodeToByteArray(str: String, flags: Int = 0): ByteArray {
        val decoder = if (flags and URL_SAFE != 0) {
            Base64.getUrlDecoder()
        } else if (flags and (NO_WRAP or CRLF) != 0) {
            // NO_WRAP/CRLF 为换行相关，用宽松 MIME 解码器兼容
            Base64.getMimeDecoder()
        } else {
            Base64.getDecoder()
        }
        // 无 padding 输入：先尝试标准解码，失败则补 padding 重试
        return runCatching { decoder.decode(str) }
            .getOrElse { e ->
                val padded = if (str.length % 4 == 0) str else {
                    val sb = StringBuilder(str)
                    while (sb.length % 4 != 0) sb.append('=')
                    sb.toString()
                }
                decoder.decode(padded)
            }
    }
}
