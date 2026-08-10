package io.legado.desktop.data.entities

import io.legado.desktop.help.config.AppConfig

@Suppress("ConstPropertyName")
data class BookGroup(
    val groupId: Long = 0b1,
    var groupName: String = "",
    var cover: String? = null,
    var order: Int = 0,
    var enableRefresh: Boolean = true,
    var show: Boolean = true,
    var bookSort: Int = -1,
    // 只更新已读
    var onlyUpdateRead: Boolean = false
)  {

    companion object {
        const val IdRoot = -100L
        const val IdAll = -1L
        const val IdLocal = -2L
        const val IdAudio = -3L
        const val IdNetNone = -4L
        const val IdLocalNone = -5L
        const val IdVideo = -6L
        const val IdError = -11L
    }

    fun getManageName(): String {
        return when (groupId) {
            IdAll -> "$groupName(全部)"
            IdAudio -> "$groupName(音频)"
            IdLocal -> "$groupName(本地)"
            IdNetNone -> "$groupName(网络无分组)"
            IdLocalNone -> "$groupName(本地无分组)"
            IdVideo -> "$groupName(视频)"
            IdError -> "$groupName(更新失败)"
            else -> groupName
        }
    }

    fun getRealBookSort(): Int {
        if (bookSort < 0) {
            return AppConfig.bookshelfSort
        }
        return bookSort
    }

    override fun hashCode(): Int {
        return groupId.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other is BookGroup) {
            return other.groupId == groupId
                    && other.groupName == groupName
                    && other.cover == cover
                    && other.bookSort == bookSort
                    && other.enableRefresh == enableRefresh
                    && other.onlyUpdateRead == onlyUpdateRead
                    && other.show == show
                    && other.order == order
        }
        return false
    }

}