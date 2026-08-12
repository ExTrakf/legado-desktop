# Legado Desktop

将 [Legado（开源阅读）](https://github.com/LegadoTeam/legado) 的**完整后端引擎**移植到桌面 JVM 的独立项目。
前端完全独立开发（不沿用官方 Web UI），通过 HTTP/WebSocket API 与后端通信。

> 许可证：GPL-3.0（派生自 Legado，必须保持开源）

## 架构

```
┌──────────────────────────────────────────────┐
│  composeApp/（Compose Multiplatform 前端）    │
│  - Windows/macOS/Linux 桌面前端（最小原型已实现）│
│  - 连接→书架→阅读→书源，走 docs/API.md         │
└──────────────┬───────────────────────────────┘
               │ HTTP (REST + JSON) / WebSocket
┌──────────────▼───────────────────────────────┐
│  backend/（纯 JVM Kotlin 服务，本仓库维护）    │
│  - 规则引擎（正则/XPath/JSONPath/CSS/JS 书源） │
│  - Rhino JS 引擎（htmlunit-core-js fork）     │
│  - OkHttp 网络层 + Cookie/代理                │
│  - SQLite 数据层（schema 对齐 Legado v99）    │
│  - NanoHTTPD HTTP 服务 + WebSocket            │
│  - 本地书籍解析（TXT/EPUB/MOBI/UMD）+ 缓存/下载 │
│  - WebView 兼容（JCEF）                       │
└──────────────────────────────────────────────┘
```

前后端通过 `docs/API.md` 中的接口契约解耦，后端不包含任何 UI 逻辑，
`backend` 目录是独立可构建的 Gradle 工程。

## 快速开始

环境要求：JDK 17+（已装 17.0.19 即可）

```bash
cd backend
./gradlew build          # 首次运行会自动下载 Gradle 8.14.4 与全部依赖
./gradlew run            # 启动后端，默认监听 http://127.0.0.1:2323
```

首次构建需要下载：
- Gradle 发行版 8.14.4（约 130 MB）
- Kotlin 插件与依赖库（约 100 MB）

如网络受限可手动下载 Gradle 后安装：
```bash
# 方式一：手动安装 Gradle（可选，装完可直接用 gradle 命令替代 ./gradlew）
wget https://services.gradle.org/distributions/gradle-8.14.4-bin.zip
unzip gradle-8.14.4-bin.zip -d ~/tools
export PATH=$HOME/tools/gradle-8.14.4/bin:$PATH

# 方式二：若 Maven Central 慢，可在 backend/settings.gradle.kts 配置镜像
```

## 目录结构

```
backend/
  settings.gradle.kts     # 仓库配置（mavenCentral + jitpack + 本地 third_party）
  build.gradle.kts        # 依赖与构建
  third_party/            # legado fork 的 htmlunit-core-js（Maven Central 无此版本）
  src/main/kotlin/io/legado/desktop/
    Main.kt               # 入口：启动/关闭服务 + --dao/--net/--rule/--source/--api/--local/--webview-smoke-test
    env/                  # 桌面环境抽象（替代原 Android Context/appCtx）
    data/                 # SQLite DAO 层（移植自 Room DAO）
    model/                # 引擎（analyzeRule / jsSource / webBook 等）
    api/                  # HTTP/WebSocket 控制器
    help/webView/         # WebView 兼容层（DesktopWebView 抽象 + JCEF 实现 CefEnv/CefWebView/JcefDesktopWebView）
composeApp/                # Compose Multiplatform 前端（Windows/macOS/Linux，最小原型已实现）
  src/commonMain/          # UI（连接/书架/书源/阅读）+ 数据模型 + expect ApiClient
  src/desktopMain/         # java.net.http actual + application 入口
docs/
  ARCHITECTURE.md         # 移植架构说明
  API.md                  # 前后端接口契约
  PLAN.md                 # 移植规划表（Part 0~7）
  HANDOVER.md             # 交接文档（经验与坑，会话必读）
  WEBVIEW-COMPOSE-PLAN.md # WebView 兼容 + Compose 前端详细规划
  GAPS.md                 # 未完成移植项清单（全量复核发现）
  ROADMAP.md              # 剩余开发路线（前端完善 / WebView 集成 / 未移植后端边界）
frontend/
  README.md               # 前端说明（你自建；Compose 前端见 composeApp/）
```

## 移植状态

- [x] 项目骨架、构建链、最小 HTTP 服务（/api/health）
- [x] 数据层：SQLite DAO（Room → sqlite-jdbc，**24/24 DAO 完成**，schema 对齐 Legado v99）
- [x] 配置与网络层：JSON 配置系统（AppConfig/LocalConfig/SourceConfig）+ OkHttp（StrResponse/SSL/gzip/deflate/brotli 解压）+ Cookie 持久化 + HTTP/SOCKS5 代理
- [x] **规则引擎（Part 3）**：AnalyzeRule（CSS/XPath/JSONPath/Regex/JS 复合规则）+ AnalyzeUrl（key/page/{{js}}/@js/POST）+ Rhino（jsSource mainJs/java 绑定/CryptoJS）
- [x] **书源与读书引擎（Part 4）**：SourceHelp / jsSource / WebBook
- [x] **API 层（Part 5）**：HttpServer 全路由 + WebSocket 搜索/调试 + 书源/RSS/书籍/替换规则/HTTP 日志 API
- [x] **本地书籍解析（Part 6）**：TXT/EPUB/MOBI/UMD + 封面/图片 + 备份导入
- [x] **后端补全（GAPS.md 真缺口）**：默认数据导入（keyboardAssists seed + `/restoreDefaultData`）+ 缓存书籍（CacheBook）+ 下载（Download）+ rar/7z 解压
- [ ] **Part 7**：引擎层 T7.0~T7.5 完成（JCEF 直连，`--webview-smoke-test` 15 项断言含 4 项真实浏览器，连跑 4 次全绿）+ 前端 T7.6 骨架与 T7.7 最小原型完成（composeApp/，连接→书架→阅读→书源）；T7.7 完善（搜索/进度保存/书源导入）+ T7.8 前端 WebView 集成待做

> 数据层细节：24 张实体表 + `book_sources_part` 视图（schema v99）；DAO 接口与 Legado 一致，
> SQL 逐条对照原版 Room `@Query`；`--dao-smoke-test` 全量冒烟（24 DAO CRUD + flow +
> collate localized + IN(:list) 展开）由 `tools/test_backend.sh` 集成验证。
>
> 配置/网络层细节：`--net-smoke-test` 冒烟（16 项断言：配置读写+重启保持、gzip/deflate/brotli 解压、
> Cookie session+persistent 落库与请求注入、HTTP 代理真实链路、SOCKS5 RFC1929 握手、真实 https）；也由 `tools/test_backend.sh` 集成。
>
> 规则引擎细节：`--rule-smoke-test` 冒烟（23 项断言：JSoup/XPath/JSONPath/复合规则/变量、
> AnalyzeUrl 真实请求、RhinoScriptEngine/java 绑定/CryptoJS、JS 源 mainJs、规则源+JS 源全链路）；也由 `tools/test_backend.sh` 集成。
>
> 书源/读书引擎细节：`--source-smoke-test` 冒烟（18 项断言：SourceHelp 导入/启停/删除、JsSourceUpsert、
> CheckSource 全项校验 PASSED、jsSource 搜索/详情/目录/正文、WebBook 全链路、ContentProcessor 替换净化、
> SearchModel 多源合并、进度存取）；也由 `tools/test_backend.sh` 集成。
>
> API 层细节：`--api-smoke-test` 冒烟（29 项断言：HttpServer 全路由 + CORS 预检 + 令牌保护 + 404、
> 书源/RSS/替换规则/HTTP 日志/阅读配置 API、书籍 API 书架/目录/正文/进度、WebSocket searchBook +
> bookSourceDebug + rssSourceDebug 结果流、导入源→搜索→加书架→目录→正文→进度端到端）；也由 `tools/test_backend.sh` 集成。
>
> 本地书籍细节：`--local-smoke-test` 冒烟（TXT/EPUB 导入→目录→正文、封面/正文图片返回字节、
> 备份导出→清库→恢复→数据一致、Legado 备份 fixture 导入）；vendored `me.ag2s.epublib/umdlib` +
> `lib.mobi`（Android 专属面仅 Log/Base64/PFD/SparseArray 等，均等价替换）；也由 `tools/test_backend.sh` 集成。

## Part 7：WebView 兼容 + Compose Multiplatform 前端（引擎层完成，前端最小原型）

恢复原版被裁剪的 WebView 能力（`BackstageWebView` 后台无头执行 JS、`@webjs:` 规则、
`AnalyzeUrl.useWebView` 分支、`JsExtensions.webView*`），并用 Compose Multiplatform
统一管理桌面前端（Windows/macOS/Linux，除 Android 外）。详细方案见
`docs/WEBVIEW-COMPOSE-PLAN.md`。

**已确认决策（2026-08-12 更新）**：
- WebView 库：**JCEF 直连**（`me.friwi:jcefmaven:146.0.10`；原规划 KCEF 已归档废弃）
- 引擎层（T7.0~T7.5）：**已完成**——`--webview-smoke-test` 15 项断言（11 纯逻辑 + 4 真实 JCEF）连跑 3 次全绿；
  JCEF 隐藏窗口承载浏览器 + `__legadoEval`/cefQuery JS 往返 + JS 桥 Proxy 反射 + `_memData` 同步
- 前端（T7.6 骨架 + T7.7 最小原型）：**已完成**——`composeApp/` KMP 工程（Compose 1.11.x），
  连接后端→书架→阅读→书源四页面，走 API.md；搜索/进度保存/书源导入与前端 WebView 集成（T7.8）待做
- 启用后端 WebView：环境变量 `LEGADO_DESKTOP_ENABLE_JCEF=1`（首次运行下载 Chromium bundle ~350MB）
- 网页登录：最小可用阶段不包含；过渡方案 = API 返回登录 URL → 系统浏览器登录 → 手动填 Cookie 到设置

## 明确不移植（初版禁用）

- TTS / 朗读 / 音频播放（含 help/audio、AudioPlay、ReadAloud 等）
- 视频播放 / 弹幕（VideoPlay、gsyVideoPlayer）
- Android UI / 通知 / 前台服务 / 广播 / 桌面部件 / ContentProvider
- 应用内更新 / 崩溃统计（Firebase）
- **AutoTask 定时任务引擎、WebDAV**（2026-08-12 用户拍板不做；数据层已迁）
- WebView 依赖功能：~~初版禁用~~ → **Part 7 已恢复**（JCEF 桌面等价实现；Android 专属
  `addJavascriptInterface` 用 JS 注入 + `window.legadoJsBridgeResult` 回调等价替代）
