# 剩余开发路线（非后端 · 待开发）

> 更新：2026-08-12
> 后端引擎与补全已全部完成（见 STATUS.json / GAPS.md）。本文件列：
> 1. **剩余非后端必要开发**（前端 composeApp/：T7.7 完善 + T7.8 WebView 集成 + Part 7 联测 + 工程化）
> 2. **仍未移植的后端内容**（明确不移植 / 用户拍板不做——作为边界参考，非待开发）
>
> 关联：PLAN.md（Part 7.5）｜WEBVIEW-COMPOSE-PLAN.md（前端方案）｜API.md（接口契约）｜GAPS.md（后端缺口）

---

## 1. 前端开发（composeApp/，KMP，Windows/macOS/Linux）

现状：最小原型已通——Connect(`/api/health`) → Bookshelf(`/getBookshelf`) → Read(`/getChapterList`+`/getBookContent` 双栏) → Sources(`/getBookSources`)。
依赖：commonMain（UI+@Serializable 模型+expect ApiClient）/ desktopMain（java.net.http actual）。

### 1.1 T7.7 前端完善（最小原型 → 可用闭环）

| # | 项 | 后端接口 | 说明 |
|---|---|---|---|
| 1 | **书源导入/管理** | `POST /saveBookSource(s)`、`/deleteBookSources`、`POST /saveJsSource` | 粘贴 JSON / 导入文件；启停/删除/分组（`/getBookSources`） |
| 2 | **搜索页** | `WS /searchBook` | 前端需 WebSocket 客户端（令牌 `Sec-WebSocket-Protocol: legado, legado.token.<t>`）；关键词 → 结果流 → 加书架 |
| 3 | **阅读进度保存** | `POST /saveBookProgress` | 切章/离开时保存 durChapterIndex/pos；进入默认定位上次进度 |
| 4 | **书架增强** | `/cover?path=` + 图片加载 | 封面显示；分组/排序（AppConfig.bookshelfSort）；下拉刷新 |
| 5 | **阅读体验** | `/getBookContent`、`/image` | 分页/滚动、字号/主题、目录跳转、上下章、正文图片 |
| 6 | **连接/令牌管理** | 全 API | 多后端配置记忆、重连提示、`x-legado-token` 设置（写路由需令牌） |

### 1.2 T7.8 前端 WebView 集成

- 前端可见 WebView 组件（方案见 WEBVIEW-COMPOSE-PLAN.md §2：KCEF/compose-webview 或 JCEF 直连，前端层独立于后端引擎）
- **网页登录**（过渡方案）：后端/前端提供登录 URL → 系统浏览器完成 → 用户手动填 Cookie 到设置页 → 存回 `CookieStore`；内嵌 WebView 自动登录归入后续
- 网页书源浏览（书源内 WebView 内容展示）
- 与后端 JCEF 引擎的关系：**后端 WebView** 服务规则引擎（`@webjs:`/`AnalyzeUrl.useWebView`/`JsExtensions.webView*`）；**前端 WebView** 服务 UI 展示——两者独立

### 1.3 Part 7 联测

- `tools/test_backend.sh` 已有 4.12 webview 段（真实 JCEF 段依赖已下载的 bundle，无 bundle 时 `[SKIP]` 降级）
- 前后端端到端：启动后端（`LEGADO_DESKTOP_ENABLE_JCEF=1` 启用 WebView）→ 前端连接 → 导入书源 → 搜索 → 加书架 → 阅读 → 进度

### 1.4 前端工程化（必要非功能项）

- `./gradlew createDistributable` 打包（Windows/Mac/Linux 分发）
- 前端自检（连后端 health + 关键 API 断言）

---

## 2. 仍未移植的后端内容（明确不移植 / 用户拍板不做）

> 以下不是"待开发"，是**明确不做**；列入文档作为边界参考，避免误认为遗漏或日后误改。

### 2.1 用户拍板不做（2026-08-12）

- **AutoTask 定时任务引擎**：`model/AutoTask.kt`、`AutoTaskRunner.kt`、`AutoTaskProtocol.kt`、`AutoTaskSchedulePolicy.kt`（数据层 DAO/实体/`CronSchedule` 已迁）
- **WebDAV**：`help/AppWebDav.kt`、`lib/webdav/`、`model/remote/`（`Server` 数据/备份 AES 已迁）

### 2.2 明确不移植（README「明确不移植」+ STATUS.json `constraints.notDoing`）

- **TTS/朗读/音频**：`help/audio/`、`AudioPlay`、`ReadAloud`、`ReadAloudManualPagePolicy`、`help/exoplayer/`、`AudioCacheKey/StateChanged`
- **视频/弹幕/漫画**：`help/gsyVideo/`、`VideoPlay`、`ReadManga`
- **Glide 图片加载**：`help/glide/`（桌面用 OkHttp + 文件读）
- **Cronet**：`lib/cronet/`、`help/http/Cronet.kt`
- **应用更新/Firebase**：`help/update/`、`CrashHandler`
- **Android UI 全套**：通知/前台服务（`receiver/` `service/` `base/`）、权限（`lib/permission/`）、偏好控件（`lib/prefs/`）、主题（`lib/theme/`、`ThemeConfig`）、对话框（`lib/dialogs/`）、快捷方式/ContentProvider（`ShortCuts`、`ReaderProvider`）、CanvasRecorder、objectpool、viewbindingdelegate、SAF/Document utils（`DocumentUtils`、`FileDocExtensions`、`UriExtensions` 等）
- **MCP**：`web/mcp/`
- **评论 API**：`ReviewController`
- **静态资源伺服**：`web/utils/AssetsWeb.kt`（前端分离）
- **WebView 登录 UI 渲染** / RSS 可见 WebView / webkit Cookie 同步（桌面纯 HTTP Cookie）
- **ReadBook 全局阅读状态**（桌面由前端持有阅读态；纯逻辑已吸收到 `ReadRecordIndex`/`BookHelp` 等）
- **其余 Android 专属 utils**：Activity/Fragment/View/Toast/Intent/QRCode/Color/动画/弹窗等

---

## 3. 建议开发顺序（前端）

1. **前端工程化骨架**（打包 + 自检）—— 保障可运行、可交付
2. **书源导入/管理**（1.1-1）—— 后续搜索/阅读的前提
3. **搜索页**（1.1-2，WS 客户端）—— 多源搜索闭环
4. **阅读进度保存**（1.1-3）—— 阅读连续性
5. **书架/阅读体验增强**（1.1-4/5，封面/分组/翻页）
6. **连接/令牌管理**（1.1-6）
7. **T7.8 前端 WebView 集成**（网页登录/网页书源）
8. **Part 7 联测 + 分发打包**

> 每项遵循既有纪律：走 API.md 契约、ASCII 输出（教训28）、完成后同步 STATUS.json/HANDOVER.md。
