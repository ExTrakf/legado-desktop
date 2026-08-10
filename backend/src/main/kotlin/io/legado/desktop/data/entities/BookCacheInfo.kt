package io.legado.desktop.data.entities

import io.legado.desktop.constant.AppPattern
import io.legado.desktop.constant.BookType
import io.legado.desktop.utils.MD5Utils
import kotlin.math.min

data class BookCacheInfo(
    val bookUrl: String,
    val name: String,
    val origin: String,
    val originName: String,
    val type: Int,
)

data class BookCacheCleanupSnapshot(
    val books: List<BookCacheInfo>,
    val imageBooks: List<Book>,
)

internal fun BookCacheInfo.getFolderName(): String {
    return name.replace(AppPattern.fileNameRegex, "").let {
        it.substring(0, min(9, it.length)) + MD5Utils.md5Encode16(bookUrl)
    }
}

internal val BookCacheInfo.isEpub: Boolean
    get() = isLocal && originName.endsWith(".epub", ignoreCase = true)

private val BookCacheInfo.isLocal: Boolean
    get() {
        if (type == 0) {
            return origin == BookType.localTag || origin.startsWith(BookType.webDavTag)
        }
        return type and BookType.local > 0
    }
