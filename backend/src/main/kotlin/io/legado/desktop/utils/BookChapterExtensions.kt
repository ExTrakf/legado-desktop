package io.legado.desktop.utils

import io.legado.desktop.data.entities.BookChapter

fun BookChapter.internString() {
    title = title.intern()
    bookUrl = bookUrl.intern()
}
