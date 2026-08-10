package io.legado.desktop.data.entities



data class SearchKeyword(
    /** 搜索关键词 */
    var word: String = "",
    /** 使用次数 */
    var usage: Int = 1,
    /** 最后一次使用时间 */
    var lastUseTime: Long = System.currentTimeMillis()
) 
