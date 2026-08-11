package io.legado.desktop.lib.mobi.entities

import io.legado.desktop.lib.mobi.SparseArray

data class IndexEntry(
    val label: String,
    val tags: List<IndexTag>,
    val tagMap: SparseArray<IndexTag>
)
