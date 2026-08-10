package io.legado.desktop.data.entities

import io.legado.desktop.help.HighlightStyle
import io.legado.desktop.utils.GSON
import io.legado.desktop.utils.fromJsonObject

data class BookHighlight(
    val time: Long = System.currentTimeMillis(),
    var bookUrl: String = "",
    var chapterUrl: String = "",
    val bookName: String = "",
    val bookAuthor: String = "",
    var chapterIndex: Int = 0,
    var chapterPos: Int = 0,
    var chapterPosEnd: Int = 0,
    var layoutTitleLength: Int = UNKNOWN_TITLE_LENGTH,
    var chapterName: String = "",
    var bookText: String = "",
    var style: String = "",
    var note: String = ""
)  {

    @Transient
    private var styleCache: Pair<String, HighlightStyle>? = null

    fun styleObj(): HighlightStyle {
        styleCache?.takeIf { it.first == style }?.second?.let { return it }
        return (GSON.fromJsonObject<HighlightStyle>(style).getOrNull() ?: HighlightStyle()).normalized()
            .also { styleCache = style to it }
    }

    fun applyStyle(s: HighlightStyle) {
        val normalized = s.normalized()
        style = GSON.toJson(normalized)
        styleCache = style to normalized
    }

    fun bindLegacyOwner(bookUrl: String, chapterUrl: String) {
        if (this.bookUrl.isBlank()) this.bookUrl = bookUrl
        if (this.chapterUrl.isBlank()) this.chapterUrl = chapterUrl
    }

    fun bodyStart(currentTitleLength: Int): Int {
        return bodyPosition(chapterPos, currentTitleLength)
    }

    fun bodyEnd(currentTitleLength: Int): Int {
        return bodyPosition(chapterPosEnd, currentTitleLength)
    }

    fun pinLayoutTitleLength(currentTitleLength: Int): Boolean {
        if (layoutTitleLength >= 0 || currentTitleLength < 0) return false
        layoutTitleLength = currentTitleLength
        return true
    }

    private fun bodyPosition(layoutPosition: Int, currentTitleLength: Int): Int {
        val titleLength = layoutTitleLength.takeIf { it >= 0 } ?: currentTitleLength.coerceAtLeast(0)
        return (layoutPosition - titleLength).coerceAtLeast(0)
    }

    companion object {
        const val UNKNOWN_TITLE_LENGTH = -1
    }
}
