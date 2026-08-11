package io.legado.desktop.utils

import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File

/**
 * 桌面版图片工具（ImageIO 替代 Android Bitmap/BitmapFactory）。
 * 仅保留封面相关函数；原 Bitmap.compress(JPEG, 90) 语义 → ImageIO JPEG 写。
 */
@Suppress("unused")
object BitmapUtils {

    /**
     * 解码图片字节并按 JPEG 重新编码写入文件（等价原 BitmapFactory.decode + Bitmap.compress(JPEG, 90)）。
     * 无法解码时直接写原始字节（比原版 NPE 更健壮，不改业务语义）。
     */
    fun writeJpeg(bytes: ByteArray, filePath: String): Boolean {
        if (bytes.isEmpty()) return false
        val file = File(filePath)
        file.parentFile?.mkdirs()
        val image = runCatching {
            ImageIO.read(ByteArrayInputStream(bytes))
        }.getOrNull()
        return if (image != null) {
            runCatching {
                ImageIO.write(image, "jpeg", file)
            }.isSuccess
        } else {
            runCatching { file.writeBytes(bytes) }.isSuccess
        }
    }

    /** 图片字节 → 是否可解码（原 BitmapFactory.decodeStream != null） */
    fun isDecodable(bytes: ByteArray): Boolean =
        runCatching {
            ImageIO.read(ByteArrayInputStream(bytes)) != null
        }.getOrDefault(false)

    /** 图片字节 → BufferedImage（null 表示不可解码） */
    fun decode(bytes: ByteArray): BufferedImage? =
        runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull()

    /**
     * 按宽度缩放（保持宽高比），等价原 BitmapFactory 采样解码 + 前端按宽显示。
     * 不可解码时返回原始字节。
     */
    fun resize(bytes: ByteArray, width: Int): ByteArray {
        if (width <= 0) return bytes
        val image = decode(bytes) ?: return bytes
        if (image.width <= width) return bytes
        val targetW = width
        val targetH = (image.height.toLong() * targetW / image.width).toInt().coerceAtLeast(1)
        val scaled = BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB)
        val g = scaled.createGraphics()
        try {
            g.drawImage(image, 0, 0, targetW, targetH, null)
        } finally {
            g.dispose()
        }
        return java.io.ByteArrayOutputStream().use { out ->
            ImageIO.write(scaled, "jpeg", out)
            out.toByteArray()
        }
    }
}
