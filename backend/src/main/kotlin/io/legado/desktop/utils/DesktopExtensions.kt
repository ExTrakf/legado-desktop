package io.legado.desktop.utils

import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow

/** 桌面版扩展：替代 Android Context/File 相关扩展 */

/** context.getFile(name) → File 直接构造 */
fun File.getFile(vararg names: String): File = File(this, names.joinToString(File.separator))

/** File.createFileReplace() → 替换创建 */
fun File.createFileReplace(): File {
    if (exists()) delete()
    parentFile?.mkdirs()
    createNewFile()
    return this
}

/** File.createFolderReplace() → 替换创建目录 */
fun File.createFolderReplace(): File {
    if (exists()) deleteRecursively()
    mkdirs()
    return this
}

/** 字节转 16 进制字符串 */
fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

val Int.hexString: String get() = String.format("%06x", this)

/** 字符串转颜色（桌面版不渲染，返回 0） */
fun String.toColorInt(): Int = 0

fun Int.toColorInt(): Int = this

/** File.createFileIfNotExist() → 不存在则创建 */
fun File.createFileIfNotExist(): File {
    if (!exists()) {
        parentFile?.mkdirs()
        createNewFile()
    }
    return this
}

/** File.exists(vararg subDirs) → 组合子路径后判断存在 */
fun File.exists(vararg subDirs: String): Boolean {
    return File(this, subDirs.joinToString(File.separator)).exists()
}

/** 全局事件（桌面版无事件总线，保留调用点但 no-op，避免改业务逻辑） */
fun postEvent(event: String, value: Any? = null) {
    // 桌面版：事件总线由前端 API 轮询/推送替代，此处保留原调用点
}

/** 路径包含判断（原 utils.isSameOrDescendantOf，SAF 版本等价实现） */
fun java.io.File.isSameOrDescendantOf(parent: java.io.File): Boolean {
    return path == parent.path || path.startsWith(parent.path + java.io.File.separator)
}

fun String.isSameOrDescendantOf(parent: String): Boolean {
    return this == parent || startsWith(parent.trimEnd('/') + "/")
}
