package io.legado.desktop.data.entities



data class TxtTocRule(
    var id: Long = System.currentTimeMillis(),
    var name: String = "",
    var rule: String = "",
    var replacement: String = "",
    var example: String? = null,
    var serialNumber: Int = -1,
    var enable: Boolean = true
) {

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is TxtTocRule) {
            return id == other.id
        }
        return false
    }

}