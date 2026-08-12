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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
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
            Button(
                onClick = {
                    scope.launch {
                        state.baseUrl = url.trimEnd('/')
                        state.api.baseUrl = state.baseUrl
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
                            .clickable { loadContent(state, scope, ch.index) }
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
            // 默认打开当前阅读章节
            state.currentBook?.let { loadContent(state, scope, it.durChapterIndex) }
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
