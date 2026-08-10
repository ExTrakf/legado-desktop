package io.legado.desktop.data.entities

import io.legado.desktop.help.HighlightStyle
import io.legado.desktop.utils.GSON
import io.legado.desktop.utils.fromJsonObject
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

data class HighlightRule(
    var id: Long = 0,
    var name: String = "",
    var pattern: String = "",
    var isRegex: Boolean = false,
    var scope: String? = null,
    var isEnabled: Boolean = true,
    var style: String = "",
    var order: Int = Int.MIN_VALUE,
    var timeoutMillisecond: Long = DEFAULT_TIMEOUT_MILLISECONDS,
    var applyToTitle: Boolean = false
)  {

    override fun equals(other: Any?): Boolean {
        if (other is HighlightRule) return other.id == id
        return super.equals(other)
    }

    override fun hashCode(): Int = id.hashCode()

    @Transient
    private var styleCache: Pair<String, HighlightStyle>? = null

    fun styleObj(): HighlightStyle {
        styleCache?.takeIf { it.first == style }?.second?.let { return it }
        return (GSON.fromJsonObject<HighlightStyle>(style).getOrNull() ?: HighlightStyle()).normalized()
            .also { styleCache = style to it }
    }

    fun applyStyle(value: HighlightStyle) {
        val normalized = value.normalized()
        style = GSON.toJson(normalized)
        styleCache = style to normalized
    }

    fun getDisplayName(): String = name.ifBlank { pattern }

    fun isValid(): Boolean {
        if (pattern.isEmpty()) return false
        if (!isRegex) return true
        try {
            Pattern.compile(pattern)
        } catch (_: PatternSyntaxException) {
            return false
        }
        return true
    }

    @Suppress("USELESS_CAST")
    fun normalizeForRestore(): HighlightRule {
        name = (name as String?).orEmpty()
        pattern = (pattern as String?).orEmpty()
        style = (style as String?).orEmpty()
        if (style.isBlank()) applyStyle(HighlightStyle())
        if (timeoutMillisecond <= 0L) {
            timeoutMillisecond = DEFAULT_TIMEOUT_MILLISECONDS
        }
        return this
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLISECONDS = 3000L
    }
}
