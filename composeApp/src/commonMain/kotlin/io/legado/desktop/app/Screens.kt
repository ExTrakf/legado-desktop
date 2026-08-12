package io.legado.desktop.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.sp
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
    var connectMsg by remember { mutableStateOf<String?>(null) }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Legado Desktop - 连接后端") }) }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(24.dp)) {
            if (state.knownBackends.isNotEmpty()) {
                Text("历史后端（点击填充）", style = MaterialTheme.typography.bodySmall)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.knownBackends.forEach { b ->
                        TextButton(onClick = { url = b }) { Text(b) }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        scope.launch { connectBackend(state, scope, url, token) }
                    }
                ) {
                    Text("连接并进入书架")
                }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            connectMsg = applyTokenToBackend(state, token.trim())
                        }
                    }
                ) {
                    Text("应用令牌到后端")
                }
            }
            Spacer(Modifier.height(16.dp))
            if (state.loading) CircularProgressIndicator()
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            connectMsg?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            if (state.statusText.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("health: ${state.statusText}", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "提示：先启动 backend（默认 127.0.0.1:2323），再连接。" +
                    "“应用令牌到后端”会把上方令牌写进后端（等价 --set-js-source-token，无需手改 config.json）。",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private suspend fun connectBackend(state: AppState, scope: CoroutineScope, url: String, token: String) {
    val base = url.trim().trimEnd('/')
    state.baseUrl = base
    state.api.baseUrl = base
    state.api.token = token.trim()
    state.loading = true
    state.error = null
    try {
        val raw = state.api.get("/api/health")
        state.statusText = raw
        state.knownBackends = (state.knownBackends + base).distinct()
        saveSettings(
            mapOf(
                "baseUrl" to base,
                "token" to state.api.token,
                "backends" to state.knownBackends.joinToString("\n"),
            )
        )
        state.screen = Screen.Bookshelf
    } catch (e: Exception) {
        state.error = "连接失败: ${e.message}"
    } finally {
        state.loading = false
    }
}

/** 把令牌写进后端（POST /setJsSourceToken，无令牌保护），返回提示 */
private suspend fun applyTokenToBackend(state: AppState, token: String): String {
    state.api.baseUrl = state.baseUrl
    state.api.token = token
    return try {
        val resp = state.api.postJson("/setJsSourceToken", tokenForBackend(token))
        val (ok, msg) = returnStatus(resp)
        if (ok) {
            state.knownBackends = (state.knownBackends + state.baseUrl).distinct()
            saveSettings(
                mapOf(
                    "baseUrl" to state.baseUrl,
                    "token" to token,
                    "backends" to state.knownBackends.joinToString("\n"),
                )
            )
            "令牌已应用到后端"
        } else {
            "应用令牌失败: ${msg ?: "未知错误"}"
        }
    } catch (e: Exception) {
        "应用令牌失败: ${e.message}"
    }
}

// ---------------- 书架 ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(state: AppState, scope: CoroutineScope) {
    LaunchedEffect(Unit) {
        loadBooks(state, scope)
        loadGroups(state, scope)
    }
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
                    IconButton(onClick = { loadBooks(state, scope); loadGroups(state, scope) }) {
                        Icon(Icons.Default.Refresh, "刷新")
                    }
                    IconButton(onClick = { state.screen = Screen.Search }) {
                        Icon(Icons.Default.Search, "搜索")
                    }
                    IconButton(onClick = { state.screen = Screen.Sources }) {
                        Icon(Icons.Default.Menu, "书源")
                    }
                    IconButton(onClick = { state.screen = Screen.Settings }) {
                        Icon(Icons.Default.Settings, "设置")
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
            // 分组过滤（groupId 位标记；默认分组名来自 /getBookGroups）
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip("全部", state.groupFilter == -1L) { state.groupFilter = -1L }
                state.groups.forEach { g ->
                    val selected = state.groupFilter == g.groupId
                    FilterChip(g.groupName, selected) {
                        state.groupFilter = if (selected) -1L else g.groupId
                    }
                }
            }
            // 排序
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SortChip("最近阅读", state.bookSort == 0) { state.bookSort = 0 }
                SortChip("书名", state.bookSort == 1) { state.bookSort = 1 }
                SortChip("最近更新", state.bookSort == 2) { state.bookSort = 2 }
            }
            state.error?.let {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    TextButton(onClick = { state.screen = Screen.Connect }) { Text("重新连接") }
                }
            }
            if (state.statusText.isNotBlank()) {
                Text(state.statusText, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 16.dp))
            }
            val groupFilter = state.groupFilter
            val bookSort = state.bookSort
            val shown = state.books
                .filter { b -> groupFilter == -1L || (b.group.toLong() and groupFilter) != 0L }
                .let { list ->
                    when (bookSort) {
                        1 -> list.sortedBy { it.name }
                        2 -> list.sortedByDescending { it.latestChapterTime }
                        else -> list.sortedByDescending { it.durChapterTime }
                    }
                }
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.bookUrl }) { book ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clickable {
                        state.currentBook = book
                        state.screen = Screen.Read
                        loadChapters(state, scope, book.bookUrl)
                    }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            RemoteImage(state, coverPath(book.coverUrl), Modifier.size(56.dp))
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

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.small,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(end = 8.dp)
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(label, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
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

private fun loadGroups(state: AppState, scope: CoroutineScope) {
    scope.launch {
        try {
            val raw = state.api.get("/getBookGroups")
            state.groups = parseData(raw, ListSerializer(BookGroup.serializer())) ?: emptyList()
        } catch (_: Exception) {
            state.groups = emptyList()
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
    val chapterCount = state.chapters.size
    val canPrev = state.currentChapterIndex > 0
    val canNext = state.currentChapterIndex < chapterCount - 1
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.currentBook?.name ?: "阅读") },
                navigationIcon = {
                    IconButton(onClick = { state.screen = Screen.Bookshelf }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { state.fontSize = (state.fontSize - 1).coerceAtLeast(10) }) { Text("A-") }
                    TextButton(onClick = { state.fontSize = (state.fontSize + 1).coerceAtMost(48) }) { Text("A+") }
                    TextButton(onClick = { gotoChapter(state, scope, -1) }, enabled = canPrev) { Text("上一章") }
                    TextButton(onClick = { gotoChapter(state, scope, 1) }, enabled = canNext) { Text("下一章") }
                }
            )
        }
    ) { pad ->
        Row(Modifier.fillMaxSize().padding(pad)) {
            // 章节列表
            LazyColumn(Modifier.weight(0.3f).fillMaxSize()) {
                items(state.chapters, key = { it.url }) { ch ->
                    val isCurrent = ch.index == state.currentChapterIndex
                    Text(
                        ch.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                state.currentChapterIndex = ch.index
                                loadContent(state, scope, ch.index)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (isCurrent) androidx.compose.ui.text.font.FontWeight.Bold else null
                    )
                    HorizontalDivider()
                }
            }
            // 正文
            Column(Modifier.weight(0.7f).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                if (state.loading) CircularProgressIndicator()
                state.chapters.getOrNull(state.currentChapterIndex)?.let { ch ->
                    Text(ch.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                }
                val segments = remember(state.content) { parseContentImages(state.content) }
                val origin = state.currentBook?.origin
                segments.forEach { seg ->
                    when (seg) {
                        is ContentSeg.Text -> Text(seg.text, fontSize = state.fontSize.sp, lineHeight = (state.fontSize * 1.6).sp)
                        is ContentSeg.Img -> {
                            val url = resolveImageUrl(seg.url, origin)
                            if (url != null) {
                                Spacer(Modifier.height(8.dp))
                                RemoteImage(
                                    state,
                                    url,
                                    Modifier.fillMaxWidth().heightIn(max = 480.dp),
                                    placeholder = "图片"
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun gotoChapter(state: AppState, scope: CoroutineScope, delta: Int) {
    val size = state.chapters.size
    if (size == 0) return
    val next = (state.currentChapterIndex + delta).coerceIn(0, size - 1)
    if (next != state.currentChapterIndex) {
        state.currentChapterIndex = next
        loadContent(state, scope, next)
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
            Text("当前书源（点击启停，删除用右侧按钮）", style = MaterialTheme.typography.titleSmall)
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

// ---------------- 设置（T7.8 网页登录过渡 + Cookie 管理） ----------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: AppState, scope: CoroutineScope) {
    var loginUrl by remember { mutableStateOf("") }
    var cookieUrl by remember { mutableStateOf("") }
    var cookieValue by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { loadCookies(state, scope) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = { state.screen = Screen.Bookshelf }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("网页登录（过渡方案）", style = MaterialTheme.typography.titleMedium)
            Text(
                "需要登录的网页书源：点“打开登录页”用系统浏览器完成登录，" +
                    "然后把浏览器里的 Cookie（如 DevTools 复制）粘到下方“Cookie 管理”保存，" +
                    "后端书源请求会自动携带。",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = loginUrl,
                    onValueChange = { loginUrl = it },
                    label = { Text("书源站点 URL") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { loginUrl.trim().takeIf { it.isNotBlank() }?.let { openInBrowser(it) } },
                    enabled = loginUrl.isNotBlank()
                ) { Text("打开登录页") }
            }
            Spacer(Modifier.height(24.dp))
            Text("Cookie 管理", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = cookieUrl,
                onValueChange = { cookieUrl = it },
                label = { Text("站点 URL 或域名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = cookieValue,
                onValueChange = { cookieValue = it },
                label = { Text("Cookie（完整 k=v; k2=v2 串）") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { scope.launch { saveCookie(state, scope, cookieUrl.trim(), cookieValue.trim()) } },
                    enabled = cookieUrl.isNotBlank()
                ) { Text("保存 Cookie") }
                OutlinedButton(onClick = { scope.launch { clearAllCookies(state, scope) } }) {
                    Text("清空全部")
                }
                OutlinedButton(onClick = { loadCookies(state, scope) }) { Text("刷新") }
            }
            state.settingsMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp))
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 4.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("已保存 Cookie（${state.cookies.size}）", style = MaterialTheme.typography.titleSmall)
            state.cookies.forEach { c ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.url, style = MaterialTheme.typography.titleSmall)
                            Text(c.cookie, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { scope.launch { removeCookie(state, scope, c.url) } }) {
                            Text("删除")
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("连接信息", style = MaterialTheme.typography.titleMedium)
            Text(
                "后端: ${state.baseUrl}  令牌已配置: ${if (state.api.token.isNotBlank()) "是" else "否"}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun loadCookies(state: AppState, scope: CoroutineScope) {
    scope.launch {
        state.settingsMessage = null
        state.error = null
        try {
            val raw = state.api.get("/getCookies")
            state.cookies = parseData(raw, ListSerializer(Cookie.serializer())) ?: emptyList()
        } catch (e: Exception) {
            state.error = "加载 Cookie 失败: ${e.message}"
        }
    }
}

private suspend fun saveCookie(state: AppState, scope: CoroutineScope, url: String, cookie: String) {
    state.settingsMessage = null
    state.error = null
    try {
        val resp = state.api.postJson("/setCookie", cookieForSet(url, cookie))
        val (ok, msg) = returnStatus(resp)
        state.settingsMessage = if (ok) "Cookie 已保存" else ("保存失败: ${msg ?: "未知错误"}")
        loadCookies(state, scope)
    } catch (e: Exception) {
        state.error = "保存 Cookie 失败: ${e.message}"
    }
}

private suspend fun removeCookie(state: AppState, scope: CoroutineScope, url: String) {
    state.settingsMessage = null
    state.error = null
    try {
        state.api.postJson("/clearCookies", cookieForClear(url))
        loadCookies(state, scope)
    } catch (e: Exception) {
        state.error = "删除 Cookie 失败: ${e.message}"
    }
}

private suspend fun clearAllCookies(state: AppState, scope: CoroutineScope) {
    state.settingsMessage = null
    state.error = null
    try {
        state.api.postJson("/clearCookies", cookieForClear())
        state.settingsMessage = "已清空全部 Cookie"
        loadCookies(state, scope)
    } catch (e: Exception) {
        state.error = "清空 Cookie 失败: ${e.message}"
    }
}
