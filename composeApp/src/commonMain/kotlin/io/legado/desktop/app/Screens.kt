package io.legado.desktop.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer

// ---------------- 连接配置 ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectScreen(state: AppState, scope: CoroutineScope) {
    var url by remember { mutableStateOf(state.baseUrl) }
    var token by remember { mutableStateOf(state.api.token) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Legado Desktop - 连接后端") }) }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(24.dp)) {
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("后端地址") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Web 书源访问令牌（可选；导入源/搜索需要）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    scope.launch {
                        state.baseUrl = url.trimEnd('/')
                        state.api.baseUrl = state.baseUrl
                        state.api.token = token.trim()
                        state.loading = true
                        state.error = null
                        try {
                            val raw = state.api.get("/api/health")
                            state.statusText = raw
                            state.screen = Screen.Bookshelf
                        } catch (e: Exception) {
                            state.error = "连接失败: ${e.message}"
                        } finally {
                            state.loading = false
                        }
                    }
                }
            ) {
                Text("连接并进入书架")
            }
            Spacer(Modifier.height(16.dp))
            if (state.loading) CircularProgressIndicator()
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.statusText.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("health: ${state.statusText}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "提示：先启动 backend（默认 127.0.0.1:2323），再连接。",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// ---------------- 书架 ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(state: AppState, scope: CoroutineScope) {
    LaunchedEffect(Unit) { loadBooks(state, scope) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("书架") },
                navigationIcon = {
                    IconButton(onClick = { state.screen = Screen.Connect }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { loadBooks(state, scope) }) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                    IconButton(onClick = { state.screen = Screen.Search }) {
                        Icon(Icons.Default.Search, "搜索")
                    }
                    IconButton(onClick = { state.screen = Screen.Sources }) {
                        Icon(Icons.Default.Menu, "书源")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { addLocalBook(state, scope) }) {
                    Text("添加本地书籍")
                }
                Spacer(Modifier.width(12.dp))
                if (state.loading) CircularProgressIndicator()
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }
            if (state.statusText.isNotBlank()) {
                Text(state.statusText, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp))
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.books, key = { it.bookUrl }) { book ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clickable {
                        state.currentBook = book
                        state.screen = Screen.Read
                        loadChapters(state, scope, book.bookUrl)
                    }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Book, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(book.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${book.author}  ${book.originName}  读到${book.durChapterTitle ?: "第${book.durChapterIndex + 1}章"}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun addLocalBook(state: AppState, scope: CoroutineScope) {
    scope.launch {
        state.loading = true
        state.error = null
        try {
            val file = pickLocalBookFile() ?: return@launch
            val resp = state.api.postMultipart("/addLocalBook", file.fileName, file.bytes)
            state.statusText = if (resp.contains("\"isSuccess\":true")) "导入成功: ${file.fileName}" else "导入返回: $resp"
            loadBooks(state, scope)
        } catch (e: Exception) {
            state.error = "导入本地书失败: ${e.message}"
        } finally {
            state.loading = false
        }
    }
}

private fun loadBooks(state: AppState, scope: CoroutineScope) {
    scope.launch {
        state.loading = true
        state.error = null
        try {
            val raw = state.api.get("/getBookshelf")
            state.books = parseData(raw, ListSerializer(Book.serializer())) ?: emptyList()
        } catch (e: Exception) {
            state.error = "加载书架失败: ${e.message}"
        } finally {
            state.loading = false
        }
    }
}

// ---------------- 书源 ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceScreen(state: AppState, scope: CoroutineScope) {
    LaunchedEffect(Unit) {
        scope.launch {
            state.loading = true
            state.error = null
            try {
                val raw = state.api.get("/getBookSources")
                state.sources = parseData(raw, ListSerializer(BookSource.serializer())) ?: emptyList()
            } catch (e: Exception) {
                state.error = "加载书源失败: ${e.message}"
            } finally {
                state.loading = false
            }
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("书源（${state.sources.size}）") },
                navigationIcon = {
                    IconButton(onClick = { state.screen = Screen.Bookshelf }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    Button(onClick = { state.screen = Screen.SourceManage }) {
                        Text("管理")
                    }
                }
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            items(state.sources, key = { it.bookSourceUrl }) { s ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.bookSourceName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${s.bookSourceUrl}  ${s.bookSourceGroup ?: "未分组"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Text(if (s.enabled) "已启用" else "已停用")
                    }
                }
            }
        }
    }
}

// ---------------- 阅读 ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadScreen(state: AppState, scope: CoroutineScope) {
    // 进入阅读即保存当前章节进度（切章/初次加载后都会触发）
    LaunchedEffect(state.currentChapterIndex) {
        saveProgress(state, scope)
    }
    LaunchedEffect(Unit) {
        state.currentBook?.let { book ->
            state.currentChapterIndex = book.durChapterIndex
            loadContent(state, scope, book.durChapterIndex)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.currentBook?.name ?: "阅读") },
                navigationIcon = {
                    IconButton(onClick = { state.screen = Screen.Bookshelf }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { pad ->
        Row(Modifier.fillMaxSize().padding(pad)) {
            // 章节列表
            LazyColumn(Modifier.weight(0.3f).fillMaxSize()) {
                items(state.chapters, key = { it.url }) { ch ->
                    Text(
                        ch.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                state.currentChapterIndex = ch.index
                                loadContent(state, scope, ch.index)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    HorizontalDivider()
                }
            }
            // 正文
            Column(Modifier.weight(0.7f).fillMaxSize().padding(16.dp)) {
                if (state.loading) CircularProgressIndicator()
                Text(state.content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun loadChapters(state: AppState, scope: CoroutineScope, bookUrl: String) {
    scope.launch {
        state.loading = true
        state.error = null
        try {
            val raw = state.api.get("/getChapterList?url=${urlEncode(bookUrl)}")
            state.chapters = parseData(raw, ListSerializer(BookChapter.serializer())) ?: emptyList()
            // 默认打开当前阅读章节（定位上次进度）
            state.currentBook?.let { book ->
                state.currentChapterIndex = book.durChapterIndex
                loadContent(state, scope, book.durChapterIndex)
            }
        } catch (e: Exception) {
            state.error = "加载目录失败: ${e.message}"
        } finally {
            state.loading = false
        }
    }
}

private fun loadContent(state: AppState, scope: CoroutineScope, index: Int) {
    val book = state.currentBook ?: return
    scope.launch {
        state.loading = true
        try {
            val raw = state.api.get("/getBookContent?url=${urlEncode(book.bookUrl)}&index=$index")
            state.content = parseData(raw, String.serializer()) ?: "（正文为空或解析失败）\n$raw"
        } catch (e: Exception) {
            state.content = "加载正文失败: ${e.message}"
        } finally {
            state.loading = false
        }
    }
}

private fun saveProgress(state: AppState, scope: CoroutineScope) {
    val book = state.currentBook ?: return
    val idx = state.currentChapterIndex
    val title = state.chapters.getOrNull(idx)?.title
    scope.launch {
        runCatching {
            state.api.postJson("/saveBookProgress", progressForShelf(book, idx, title, 0))
        }
    }
}

// ---------------- 书源管理 ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourceManageScreen(state: AppState, scope: CoroutineScope) {
    var sourceJson by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("书源管理") },
                navigationIcon = {
                    IconButton(onClick = { state.screen = Screen.Sources }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { loadSources(state, scope) }) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 12.dp)) {
            OutlinedTextField(
                value = sourceJson,
                onValueChange = { sourceJson = it },
                label = { Text("粘贴书源 JSON 或 JS 源码") },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            state.sourceMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 4.dp))
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { scope.launch { importSourceJson(state, scope, sourceJson) } },
                    enabled = sourceJson.isNotBlank()
                ) { Text("导入") }
                Button(
                    onClick = { scope.launch { importJsSource(state, scope, sourceJson) } },
                    enabled = sourceJson.isNotBlank()
                ) { Text("导入 JS 源") }
            }
            Text("当前书源（点击启停，长按删除未实现，删除用右侧按钮）", style = MaterialTheme.typography.titleSmall)
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.sources, key = { it.bookSourceUrl }) { s ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.bookSourceName, style = MaterialTheme.typography.titleSmall)
                                Text(s.bookSourceUrl, style = MaterialTheme.typography.bodySmall)
                            }
                            Button(onClick = {
                                scope.launch { toggleSourceEnable(state, scope, s) }
                            }) {
                                Text(if (s.enabled) "停用" else "启用")
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                scope.launch { deleteSource(state, scope, s) }
                            }) {
                                Text("删除")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun loadSources(state: AppState, scope: CoroutineScope) {
    scope.launch {
        state.loading = true
        state.error = null
        try {
            val raw = state.api.get("/getBookSources")
            state.sources = parseData(raw, ListSerializer(BookSource.serializer())) ?: emptyList()
        } catch (e: Exception) {
            state.error = "加载书源失败: ${e.message}"
        } finally {
            state.loading = false
        }
    }
}

/** 导入书源 JSON（POST /saveBookSource） */
private suspend fun importSourceJson(state: AppState, scope: CoroutineScope, json: String) {
    state.sourceMessage = null
    state.error = null
    try {
        val raw = state.api.postJson("/saveBookSource", json)
        val (ok, msg) = returnStatus(raw)
        state.sourceMessage = if (ok) "导入成功" else ("导入失败: ${msg ?: "未知错误"}")
        loadSources(state, scope)
    } catch (e: Exception) {
        state.error = "导入失败: ${e.message}"
    }
}

/** 导入 JS 书源（POST /saveJsSource，text/plain） */
private suspend fun importJsSource(state: AppState, scope: CoroutineScope, js: String) {
    state.sourceMessage = null
    state.error = null
    try {
        val raw = state.api.postText("/saveJsSource", js)
        val (ok, msg) = returnStatus(raw)
        state.sourceMessage = if (ok) "JS 源导入成功" else ("JS 源导入失败: ${msg ?: "未知错误"}")
        loadSources(state, scope)
    } catch (e: Exception) {
        state.error = "JS 源导入失败: ${e.message}"
    }
}

/** 切换书源启用状态（POST /saveBookSources，传源列表） */
private suspend fun toggleSourceEnable(state: AppState, scope: CoroutineScope, s: BookSource) {
    state.sourceMessage = null
    state.error = null
    try {
        val updated = state.sources.map {
            if (it.bookSourceUrl == s.bookSourceUrl) it.copy(enabled = !s.enabled) else it
        }
        val raw = state.api.postJson("/saveBookSources", encodeSourceList(updated))
        val (ok, msg) = returnStatus(raw)
        state.sourceMessage = if (ok) "已更新" else ("更新失败: ${msg ?: "未知错误"}")
        loadSources(state, scope)
    } catch (e: Exception) {
        state.error = "更新书源失败: ${e.message}"
    }
}

/** 删除书源（POST /deleteBookSources，body 为源 url） */
private suspend fun deleteSource(state: AppState, scope: CoroutineScope, s: BookSource) {
    state.sourceMessage = null
    state.error = null
    try {
        val raw = state.api.postJson("/deleteBookSources", "\"${s.bookSourceUrl}\"")
        val (ok, msg) = returnStatus(raw)
        state.sourceMessage = if (ok) "已删除" else ("删除失败: ${msg ?: "未知错误"}")
        loadSources(state, scope)
    } catch (e: Exception) {
        state.error = "删除书源失败: ${e.message}"
    }
}

// ---------------- 搜索 ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(state: AppState, scope: CoroutineScope) {
    var keyword by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("搜索") },
                navigationIcon = {
                    IconButton(onClick = { state.screen = Screen.Bookshelf }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("关键词") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { scope.launch { runSearch(state, scope, keyword.trim()) } },
                    enabled = keyword.isNotBlank() && !state.searching
                ) { Text(if (state.searching) "搜索中" else "搜索") }
            }
            if (state.searching) {
                Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("搜索中…", style = MaterialTheme.typography.bodySmall)
                }
            }
            state.sourceMessage?.let {
                Text(it, modifier = Modifier.padding(horizontal = 12.dp), style = MaterialTheme.typography.bodySmall)
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }
            LazyColumn(Modifier.fillMaxSize().padding(bottom = 8.dp)) {
                items(state.searchResults, key = { "${it.origin}|${it.bookUrl}" }) { r ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.titleSmall)
                                Text("${r.author}  ${r.originName}", style = MaterialTheme.typography.bodySmall)
                                r.intro?.takeIf { it.isNotBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { scope.launch { addSearchedToShelf(state, scope, r) } }) {
                                Text("加书架")
                            }
                        }
                    }
                }
            }
        }
    }
}

private suspend fun runSearch(state: AppState, scope: CoroutineScope, key: String) {
    state.searchResults = emptyList()
    state.searching = true
    state.error = null
    state.sourceMessage = null
    try {
        val client = SearchClient(state.api.baseUrl, state.api.token)
        client.search(
            key,
            onResult = { frame ->
                val results = parseSearchResults(frame)
                if (!results.isNullOrEmpty()) {
                    scope.launch { state.searchResults = state.searchResults + results }
                }
            },
            onDone = { scope.launch { state.searching = false } }
        )
    } catch (e: Exception) {
        state.error = "搜索失败: ${e.message}"
        state.searching = false
    }
}

private suspend fun addSearchedToShelf(state: AppState, scope: CoroutineScope, r: SearchResult) {
    state.sourceMessage = null
    state.error = null
    try {
        val raw = state.api.postJson("/saveBook", bookForShelf(r))
        val (ok, msg) = returnStatus(raw)
        state.sourceMessage = if (ok) "已加入书架: ${r.name}" else ("加入书架失败: ${msg ?: "未知错误"}")
    } catch (e: Exception) {
        state.error = "加入书架失败: ${e.message}"
    }
}
