package io.legado.desktop.app

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

enum class Screen { Connect, Bookshelf, Sources, SourceManage, Read, Search }

/** 前端全局状态（最小原型，直接 mutableState 驱动） */
class AppState(val api: ApiClient) {
    var screen by mutableStateOf(Screen.Connect)
    var baseUrl by mutableStateOf("http://127.0.0.1:2323")
    var statusText by mutableStateOf("")
    var error by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)

    var books by mutableStateOf<List<Book>>(emptyList())
    var sources by mutableStateOf<List<BookSource>>(emptyList())
    var chapters by mutableStateOf<List<BookChapter>>(emptyList())
    var content by mutableStateOf("")
    var currentBook by mutableStateOf<Book?>(null)
    var currentChapterIndex by mutableStateOf(0)

    // 搜索
    var searchResults by mutableStateOf<List<SearchResult>>(emptyList())
    var searching by mutableStateOf(false)

    // 书源管理
    var sourceMessage by mutableStateOf<String?>(null)
}

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val state = remember {
        AppState(ApiClient("http://127.0.0.1:2323")).also {
            it.api.baseUrl = it.baseUrl
        }
    }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            when (state.screen) {
                Screen.Connect -> ConnectScreen(state, scope)
                Screen.Bookshelf -> BookshelfScreen(state, scope)
                Screen.Sources -> SourceScreen(state, scope)
                Screen.SourceManage -> SourceManageScreen(state, scope)
                Screen.Read -> ReadScreen(state, scope)
                Screen.Search -> SearchScreen(state, scope)
            }
        }
    }
}
