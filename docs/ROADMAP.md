# 剩余开发路线（非后端 · 待开发）

> 更新：2026-08-12（收尾）
> 后端引擎与补全已全部完成（见 STATUS.json / GAPS.md）。**本节所列前端工作已全部完成（T7.7 完善 + T7.8 前端集成 + 联测 + 工程化）**。
> 剩余仅：用户手动 GUI 目视验收 + 可选后续增强（内嵌 WebView 自动登录/网页书源浏览）。
> 关联：PLAN.md（Part 7.5）｜WEBVIEW-COMPOSE-PLAN.md（前端方案）｜API.md（接口契约）｜GAPS.md（后端缺口）

---

## 1. 前端开发（composeApp/，KMP，Windows/macOS/Linux）

现状：**完成**——连接(/api/health) → 书架(/getBookshelf + 封面 + 分组 + 排序) → 阅读(/getChapterList + /getBookContent 双栏 + 字号/上下章/目录高亮/正文图片 + 进度保存) → 书源(/getBookSources + 管理) → 搜索(WS /searchBook 流) → 设置(Cookie 管理 + 网页登录过渡)。
依赖：commonMain（UI+@Serializable 模型+expect ApiClient/Platform）/ desktopMain（java.net.http + Skia 解码 + Desktop.browse + 本地设置文件 actual）。

### 1.1 T7.7 前端完善（最小原型 → 可用闭环）✅ 全部完成

| # | 项 | 后端接口 | 状态 |
|---|---|---|---|
| 1 | **书源导入/管理** | `POST /saveBookSource(s)`、`/deleteBookSources`、`POST /saveJsSource` | ✅ SourceManageScreen（导入 JSON/JS 源、启停、删除） |
| 2 | **搜索页** | `WS /searchBook` | ✅ SearchScreen（SearchClient JDK WS + 结果流 + 加书架） |
| 3 | **阅读进度保存** | `POST /saveBookProgress` | ✅ 进入定位 + 切章保存 |
| 4 | **书架增强** | `/cover?path=` + `/getBookGroups` | ✅ RemoteImage 封面 + 分组过滤（位标记）+ 排序（最近阅读/书名/最近更新）+ 刷新 |
| 5 | **阅读体验** | `/getBookContent`、`/image` | ✅ A-/A+ 字号、上一章/下一章、目录当前高亮、正文 `<img>`/markdown 图片渲染 |
| 6 | **连接/令牌管理** | 全 API + `/setJsSourceToken` | ✅ 多后端记忆（~/.legado-desktop-frontend.json）、应用令牌到后端、重连提示 |

### 1.2 T7.8 前端 WebView 集成 ✅ 完成（过渡方案）

- **网页登录（过渡方案，已实现）**：设置页填站点 URL → 系统浏览器登录 → 复制 Cookie 回填 `POST /setCookie` → 后端书源请求自动携带（CookieStore）
- 设置页 Cookie 管理：查看（`GET /getCookies`）、保存/更新（`POST /setCookie`）、删除/清空（`POST /clearCookies`）
- 内嵌 WebView 自动登录、自动回传 Cookie 归入**后续可选增强**
- 与后端 JCEF 引擎的关系：**后端 WebView** 服务规则引擎（`@webjs:`/`AnalyzeUrl.useWebView`/`JsExtensions.webView*`）；前端网页登录走系统浏览器——两者独立

### 1.3 Part 7 联测 ✅

- `--api-smoke-test` 35 断言全绿（含 T7.8 令牌设置/Cookie 管理/分组 6 项）
- `tools/test_backend.sh`：4.13 新 API 契约段 + 4.14 前端编译自检 + 路径相对化（脚本位置无关）
- 其余 6 冒烟（dao25/net16/rule23/src18/local11/webview15）无回归

### 1.4 前端工程化 ✅

- `./gradlew createDistributable` → `LegadoDesktop.exe`（Compose 分发，Windows/Mac/Linux 目标）
- `tools/check_frontend.ps1`：编译 + 打包 + 后端/前端启动冒烟

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

## 3. 开发顺序（已完成回顾）

1. ✅ **前端工程化骨架**（打包 + 自检）
2. ✅ **书源导入/管理**（1.1-1）
3. ✅ **搜索页**（1.1-2，WS 客户端）
4. ✅ **阅读进度保存**（1.1-3）
5. ✅ **书架/阅读体验增强**（1.1-4/5，封面/分组/排序/字号/上下章/正文图片）
6. ✅ **连接/令牌管理**（1.1-6 + 后端 `/setJsSourceToken`）
7. ✅ **T7.8 前端 WebView 集成**（网页登录过渡 + Cookie 管理）
8. ✅ **Part 7 联测 + 分发打包**

**剩余（用户侧）**：`createDistributable` 产物 `LegadoDesktop.exe` 手动 GUI 目视验收（连接→导入源→搜索→加书架→阅读→进度→设置 Cookie）。
**后续可选增强（非阻塞）**：内嵌 WebView 自动登录/自动回传 Cookie、网页书源浏览、翻页动画/主题。

> 每项遵循既有纪律：走 API.md 契约、ASCII 输出（教训28）、完成后同步 STATUS.json/HANDOVER.md。
