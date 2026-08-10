package io.legado.desktop.constant

import org.apache.commons.lang3.time.FastDateFormat

/**
 * 桌面版常量（裁剪自 Android 版 AppConst，去掉包签名/资源/通知等 Android 专属项）
 */
@Suppress("ConstPropertyName")
object AppConst {

    const val APP_TAG = "Legado"

    const val UA_NAME = "User-Agent"

    const val MAX_THREAD = 9

    const val DEFAULT_WEBDAV_ID = -1L

    val timeFormat: FastDateFormat by lazy {
        FastDateFormat.getInstance("HH:mm")
    }

    val dateFormat: FastDateFormat by lazy {
        FastDateFormat.getInstance("yyyy/MM/dd HH:mm")
    }

    val fileNameFormat: FastDateFormat by lazy {
        FastDateFormat.getInstance("yy-MM-dd-HH-mm-ss")
    }

    const val imagePathKey = "imagePath"

    val charsets =
        arrayListOf("UTF-8", "GB2312", "GB18030", "GBK", "Unicode", "UTF-16", "UTF-16LE", "ASCII")

    /** 桌面版版本号（后端自身，与前端解耦） */
    const val VERSION = "0.1.0"
}
