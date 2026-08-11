package io.legado.desktop.model.webBook

import io.legado.desktop.data.appDb
import io.legado.desktop.data.entities.BookSource
import io.legado.desktop.data.entities.BookSourcePart
import io.legado.desktop.help.config.AppConfig
import io.legado.desktop.utils.splitNotBlank

/**
 * 桌面版 MutableLiveData 替代（原 androidx.lifecycle.MutableLiveData）。
 * 仅保留 value 语义；桌面版无 UI 观察者，postValue 直接落 value（等价无观察者场景）。
 */
@Suppress("unused")
class SimpleLiveData<T>(initial: T) {
    var value: T = initial
        private set

    fun postValue(v: T) {
        value = v
    }
}

/**
 * 搜索范围（原 io.legado.app.ui.book.search.SearchScope，纯逻辑迁移，UI 观察裁剪）。
 */
@Suppress("unused")
data class SearchScope(private var scope: String) {

    constructor(groups: List<String>) : this(groups.joinToString(","))

    constructor(source: BookSource) : this(
        "${source.bookSourceName.replace(":", "")}::${source.bookSourceUrl}"
    )

    constructor(source: BookSourcePart) : this(
        "${source.bookSourceName.replace(":", "")}::${source.bookSourceUrl}"
    )

    override fun toString(): String {
        return scope
    }

    val stateLiveData = SimpleLiveData(scope)

    fun update(scope: String, postValue: Boolean = true, save: Boolean = true) {
        this.scope = scope
        if (postValue) stateLiveData.postValue(scope)
        if (save) { //不对单书源的搜索进行缓存，防止下次依旧为单书源搜索（单书源搜索需要每次都指定）
            save()
        }
    }

    fun update(groups: List<String>) {
        scope = groups.joinToString(",")
        stateLiveData.postValue(scope)
        save()
    }

    fun update(source: BookSource) {
        scope = "${source.bookSourceName}::${source.bookSourceUrl}"
        stateLiveData.postValue(scope)
        if (!isSource()) {
            save()
        }
    }

    fun isSource(): Boolean {
        return scope.contains("::")
    }

    val display: String
        get() {
            if (scope.contains("::")) {
                return scope.substringBefore("::")
            }
            if (scope.isEmpty()) {
                return "全部书源" // 原 R.string.all_source
            }
            return scope
        }

    /**
     * 搜索范围显示
     */
    val displayNames: List<String>
        get() {
            val list = arrayListOf<String>()
            if (scope.contains("::")) {
                list.add(scope.substringBefore("::"))
            } else {
                scope.splitNotBlank(",").forEach {
                    list.add(it)
                }
            }
            return list
        }

    fun remove(scope: String) {
        if (isSource()) {
            this.scope = ""
        } else {
            val stringBuilder = StringBuilder()
            this.scope.split(",").forEach {
                if (it != scope) {
                    if (stringBuilder.isNotEmpty()) {
                        stringBuilder.append(",")
                    }
                    stringBuilder.append(it)
                }
            }
            this.scope = stringBuilder.toString()
        }
        stateLiveData.postValue(this.scope)
    }

    /**
     * 搜索范围书源
     */
    fun getBookSourceParts(): List<BookSourcePart> {
        val list = hashSetOf<BookSourcePart>()
        if (scope.isEmpty()) {
            list.addAll(appDb.bookSourceDao.allEnabledPart)
        } else {
            if (scope.contains("::")) {
                scope.substringAfter("::").let {
                    appDb.bookSourceDao.getBookSourcePart(it)?.let { source ->
                        list.add(source)
                    }
                }
            } else {
                val oldScope = scope.splitNotBlank(",")
                val newScope = oldScope.filter {
                    val bookSources = appDb.bookSourceDao.getEnabledPartByGroup(it)
                    list.addAll(bookSources)
                    bookSources.isNotEmpty()
                }
                if (oldScope.size != newScope.size) {
                    update(newScope)
                    stateLiveData.postValue(scope)
                }
            }
            if (list.isEmpty()) {
                scope = ""
                appDb.bookSourceDao.allEnabledPart.let {
                    if (it.isNotEmpty()) {
                        stateLiveData.postValue(scope)
                        list.addAll(it)
                    }
                }
            }
        }
        return list.sortedBy { it.customOrder }
    }

    fun isAll(): Boolean {
        return scope.isEmpty()
    }

    fun save() {
        AppConfig.searchScope = scope
        if (isAll() || isSource() || scope.contains(",")) {
            AppConfig.searchGroup = ""
        } else {
            AppConfig.searchGroup = scope
        }
    }

}
