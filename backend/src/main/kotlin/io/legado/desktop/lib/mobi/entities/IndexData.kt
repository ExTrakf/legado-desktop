package io.legado.desktop.lib.mobi.entities

import io.legado.desktop.lib.mobi.SparseArray

data class IndexData(
    val table: List<IndexEntry>,
    val cncx: SparseArray<String>
)
