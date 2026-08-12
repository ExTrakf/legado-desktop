# T7.7 前端完善 设计文档

> 日期：2026-08-12
> 范围：`composeApp/` 前端（Compose Multiplatform，仅 jvm(desktop)）
> 关联：ROADMAP.md §1.1、WEBVIEW-COMPOSE-PLAN.md、API.md、STATUS.json（T7.7 in_progress）

## 目标

将前端从"最小原型（连接→书架→阅读→书源）"完善为最小可用闭环：

1. **书源导入/管理**（搜索的前提）
2. **搜索页**（WebSocket 多源搜索 → 结果流 → 加书架）
3. **阅读进度保存**（进入定位上次进度、离开/切章保存）
4. **最小令牌支持**（支撑上述三项写路由与搜索握手）

未纳入本轮（后续 ROADMAP 处理，可后续规划）：书架增强（封面/分组/排序/下拉刷新）、阅读体验增强（翻页/字号/主题/正文图片）、完整连接/令牌管理、T7.8 前端 WebView 集成、Part 7 联测、前端工程化打包。

## 决策（已与用户确认）

- **范围**：仅 T7.7 前端完善。
- **核心三项**：搜索页、书源导入/管理、阅读进度保存。
- **令牌**：最小令牌支持（连接页可选令牌输入 → 写路由 + WS 握手自动携带）。
- **实现方案 A（就地扩展）**：沿用现有 `expect/actual` + 单一 `AppState`；HTTP 用 JDK `java.net.http`，**WS 用 JDK 内置 `java.net.http.WebSocket`**；零新增第三方依赖；commonMain 保持无 JVM API（教训40）。

## 架构与组件

全部在 `composeApp/` 内，遵守现有结构（commonMain UI + @Serializable 模型 + expect；desktopMain actual）。

### commonMain（`io/legado/desktop/app/`，无 JVM API）

- **`ApiClient.kt`**（扩展 expect）新增：
  - `var token: String`（`x-legado-token`，可选）
  - `suspend fun get(path): String` — 改为带 token 头（读路由无害）
  - `suspend fun postJson(path, body): String` — JSON body POST，带 token 头
  - `suspend fun postText(path, body): String` — text/plain POST（`/saveJsSource` 专用），带 token 头
- **`SearchClient.kt`**（新增 expect）：
  - `class SearchClient(baseUrl, token)`
  - `suspend fun search(key: String, onResult: (String) -> Unit, onDone: () -> Unit)` — 封装 WS `/searchBook` 结果流
- **`Models.kt`**（扩展）：按搜索结果结构补充 `@Serializable` 模型（若前端需展示新字段）；沿用 `parseData/parseDataRaw`（Json ignoreUnknownKeys）
- **`App.kt`**（扩展 AppState）：新增 `keyword`、`searchResults`、`searching`、书源列表操作状态；Screen 枚举增补 `Search`、`SourceManage`
- **`Screens.kt`**：新增 `SearchScreen`、`SourceManageScreen`；书架的"加书架"交互；阅读进度保存逻辑

### desktopMain（仅 JVM）

- **`ApiClient.desktop.kt`**（扩展 actual）：实现 `postJson`/`postText`（`java.net.http.HttpRequest` + BodyPublishers.ofString + JSON/text Content-Type），token 写 `x-legado-token` 头；`get` 补 token 头
- **`SearchClient.desktop.kt`**（新增 actual）：JDK `java.net.http.WebSocket`
  - 握手：`HttpClient.newWebSocketBuilder().subprotocols("legado", "legado.token.<t>")`
  - 连接后发送 `{"key":"<key>"}` 文本帧
  - 收文本帧 → `onResult`（原始 JSON），遇结束/关闭 → `onDone`
  - 回调在 IO 线程；UI 侧用 coroutines 汇集（照现有 `withContext(Dispatchers.IO)` 模式）

## 数据流（走 docs/API.md 契约）

### 书源导入/管理（SourceManageScreen）
- 导入：粘贴 JSON → `POST /saveBookSource`（body=书源 JSON）；JS 源 → `POST /saveJsSource`（body=JS 源文本，content-type text/plain）；本地文件 → 读文本后同上二者之一
- 列表：`GET /getBookSources`（现有）
- 启停：`POST /saveBookSources`（body=源列表 JSON）
- 删除：`POST /deleteBookSources`（body=源 url 串/列表）
- 需要令牌（写路由）

### 搜索（SearchScreen，WS 流）
- 输入关键词 → `SearchClient.search` → 结果帧**流式追加**到列表
- 每项"加书架" → `POST /saveBook`（用搜索结果对象）
- 令牌非空时握手带 `legado.token.<t>` 子协议

### 阅读进度（ReadScreen）
- 进入定位上次进度：`durChapterIndex`（现有默认打开当前章逻辑）
- 切章/离开：`POST /saveBookProgress`（body：bookUrl + durChapterIndex + pos 等字段）
- 需要令牌（写路由）

## 令牌与错误处理

- 连接页新增**可选令牌输入框**，记住到本地状态；所有写路由 + WS 握手自动携带。
- 令牌未配置时行为不变（后端默认令牌关闭）。
- 令牌错误/HTTP 非 2xx/WS 断开 → 页面错误提示 + 可重试，不崩溃。
- 各页面沿用现有 `loading/error` AppState 模式。

## 测试

- 编译自检：`./gradlew :composeApp:compileKotlinDesktop` + `desktopJar`。
- 手动端到端：启动后端（`LEGADO_DESKTOP_ENABLE_JCEF=1` 可选）→ 前端连接 → 导入书源 → 搜索 → 加书架 → 阅读 → 进度落库（本地 mock 规则源 + `getBookSources`/`getBookshelf` 核对）。
- 后端 API 已由 `--source/--api-smoke-test` 覆盖；前端侧本轮以编译 + 手动闭环为主（Part 7 联测留后续）。

## 不在本轮范围

书架增强（封面/分组/排序/下拉刷新）、阅读体验增强（翻页/字号/主题/正文图片）、完整连接/令牌管理（多后端记忆/重连）、T7.8、Part 7 联测、`createDistributable` 打包与前端自检。

## 纪律（遵循项目既有约束）

- 忠于现有模式，不引入新技术方案/依赖（JDK 内置 WS 已满足）。
- 跨平台：commonMain 无 JVM API；路径/编码规范照旧。
- 冒烟/输出禁 emoji（ASCII `[PASS]/[FAIL]`）。
- 完成后同步 STATUS.json（T7.7 置 done 前需手动闭环验收）。
