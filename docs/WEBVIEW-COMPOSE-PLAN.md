# WebView 兼容功能 + Compose Multiplatform 前端 规划

> 状态：**调研完成，待确认决策点后实施**（2026-08-10）
> 关联：PLAN.md（Part 7 新增）、ARCHITECTURE.md（前端解耦原则需修订）、STATUS.json
> 原则不变：忠于原版业务逻辑（Android→桌面等价替换）；跨平台（Windows/macOS/Linux）

---

## 1. 目标

1. **恢复 WebView 兼容功能**：原版 `BackstageWebView`（后台无头 WebView 执行 JS）等 5 个组件共 1073 行被裁剪，导致 `@webjs:` 规则、`AnalyzeUrl.useWebView` 分支、`JsExtensions.webView/webViewGetSource/webViewGetOverrideUrl`、网页登录等全部失效。本轮要恢复这些能力（桌面引擎等价实现）。
2. **引入 Compose Multiplatform**：统一管理多平台前端（**除 Android** 外），一套 Kotlin 代码跑 Windows / macOS / Linux 桌面端。

## 2. 调研总结（2026-08-10 搜索）

### 2.1 Compose Multiplatform 现状
- 最新稳定版 **1.11.1**（1.12.0 处于 beta，2026-07 发布节奏活跃）[citation,github.com](jb-compose-1.11.1)
- 官方 target：Desktop (JVM: Windows/macOS/Linux)、iOS、Web (Wasm/JS)
- Gradle 插件 `org.jetbrains.compose`；JVM toolchain 17 与当前项目一致，可直接升级

### 2.2 桌面 WebView 方案对比（重点）

| 方案 | 引擎 | 优点 | 缺点 | 结论 |
|---|---|---|---|---|
| **KevinnZou/compose-webview-multiplatform**（965★，Maven Central **2.0.3**） | v1.7.0 起 KCEF（Chromium） | 生态最主流；Android/iOS/Desktop 同 API；JS 执行强；`WebViewState/Navigator/evaluateJavaScript` | 需 `KCEF.init` + 下载/打包 ~200MB Chromium bundle；需要 JBR 或指定 jcef-bundle；要 `--add-opens` flags；首次下载体验差 | **首选**（能力对齐 Android WebView 最接近）[citation,github.com](kevinnzou-cwm) |
| **kdroidFilter/ComposeNativeWebview**（129★，v1.0.0-beta-01） | Wry (Rust) + UniFFI：Win=WebView2 / macOS=WKWebView / Linux=WebKitGTK | 体积小、启动快、用系统原生引擎；同 KevinnZou API | 仍 beta（2026-03 发布）；Rust 编译链；Linux 依赖系统 WebKitGTK；JS 桥/无头能力待验证 | **备选**（若 KCEF 体积不可接受）[citation,github.com](kdroidfilter-cnw) |
| JavaFX WebView | JavaFX WebKit | JDK 自带（需 OpenJFX 模块） | WebKit 老旧、官方已停止增强、KevinnZou 已弃用此路线 | 不采用 |
| JxBrowser (TeamDev) | Chromium | 商业支持强 | 付费授权 | 不采用 |

> 注：compose-webview-multiplatform 2.0.3 基于 Compose 1.8.0 构建，需验证与 Compose MP 1.11.1 的兼容（可能需跟随其 Compose 版本或使用其对应版本）。

### 2.3 关键决策：Compose MP 只启用 Desktop (JVM) target

当前引擎依赖**全部是 JVM 专属**：OkHttp、sqlite-jdbc、Rhino（htmlunit-core-js fork）、NanoHTTPD、jsoup、Gson、brotli。这些在 Kotlin/Native（iOS）与 Kotlin/Wasm（Web）上**不可用**，且重写违背"忠于原版"硬约束。因此：

- ✅ **Windows / macOS / Linux**（Compose Desktop = JVM）：一套 UI 代码，三平台运行
- ❌ **iOS / Web**：不可行（除非未来引擎层做抽象替换，超出当前范围）

项目结构上仍按 KMP 组织（`composeApp` 模块 + `desktopMain` source set），未来若引擎可替换再扩 target。

## 3. 原版 WebView 功能清单（忠于原版，逐组件）

来源：`legado/app/src/main/java/io/legado/app/`（commit 36d58eea）

| 组件 | 行数 | 功能 | 桌面等价实现要点 |
|---|---|---|---|
| `help/http/BackstageWebView.kt` | 394 | 后台无头加载 URL/HTML，执行 `javaScript`，支持 `sourceRegex` 提取、`overrideUrlRegex` URL 拦截、`delayTime`、`cacheFirst`、`timeout`、`headerMap`、`isRule`（注入 source/cache 接口）；`suspend getStrResponse(): StrResponse` | 用 KCEF 无头加载 + `executeJavaScript` + 结果回调；`sourceRegex` 用 Kotlin Regex 对返回 HTML 提取；`overrideUrlRegex` 用 WebView 的 URL 拦截回调 |
| `help/webView/WebJsExtensions.kt` | 423 | JS↔Kotlin 桥：注入 `window.legado` 系列接口（nameBasic/nameJava/nameSource/nameCache）；`request(funName, params, id)` 协议支持 run/ajaxAwait/connectAwait/getAwait/headAwait/postAwait/webViewAwait/webViewGetSourceAwait/decryptStrAwait/encryptBase64Await/encryptHexAwait/createSignHexAwait/downloadFileAwait/readTxtFileAwait/importScriptAwait/getStringAwait；结果写 `CacheManager.putMemory(id, data)` + `evaluateJavascript("window.legadoJsBridgeResult('$id', true/false)")` | 桌面 WebView 无 `addJavascriptInterface`，用 **JS 注入 + message handler**（KCEF 的 `JavascriptDialogHandler`/`executeJavaScript` 往返，或注入 `<script>` 定义桥 + 轮询/回调）；`request` 的 funName 分发逻辑逐字保留，仅替换 JS 往返通道 |
| `help/webView/WebViewPool.kt` | 201 | WebView 池化：idle 栈 + inUse map；容量 `max(threadCount/10, 5)`；闲置 5min/最后一个 30min 清理；acquire/release/预初始化（JS 开关、domStorage、mixedContent） | 桌面 KCEF browser 复用池（KCEF `CefBrowser` 可复用）；清理定时器用协程等价 |
| `help/webView/PooledWebView.kt` | 21 | 包装类（realWebView + id + isInUse + lastUseTime） | 直接保留（字段等价） |
| `help/webView/WebViewRequestConfig.kt` | 34 | UA 提取 + additionalHeaders（排除 cookieJarHeader） | 逻辑逐字保留 |
| `help/CacheManager.kt` 的 `WebCacheManager`（168 行起） | ~60 | JS 缓存接口（put/putMemory/getFromMemory/deleteMemory/get/putFile/getFile/delete） | 已迁移的 `CacheManager` 桌面版直接复用 |
| 调用点 | — | `AnalyzeRule.getWebJsResult`（`@webjs:` 规则）；`AnalyzeUrl` 的 `useWebView && useWebView` 分支；`JsExtensions.webView/webViewGetSource/webViewGetOverrideUrl`（现抛错） | 逐点解除裁剪、恢复原逻辑 |

**明确不移植**（仍保持裁剪）：`ui/browser/WebViewActivity`、`ui/login/WebViewLoginFragment`（登录 UI 渲染，若做 Compose 前端可另行实现等价交互）、`ui/rss/read/VisibleWebView`（仅 Android View 类型，桌面池改用 KCEF browser 类型）。

## 4. 推荐架构

```
legado-desktop/
├── backend/                      # 现有纯 JVM 引擎（不变，NanoHTTPD 服务）
│   └── ... help/webview/         # 新增：WebView 兼容层（桌面无头执行）
│       ├── BackstageWebView.kt   #   等价实现（KCEF 驱动）
│       ├── WebJsExtensions.kt    #   JS 桥（逐字保留 funName 分发）
│       ├── WebViewPool.kt        #   池化（KCEF browser 复用）
│       └── WebViewRequestConfig.kt
├── composeApp/                   # 新增：Compose Multiplatform 前端（KMP 结构）
│   ├── commonMain/               #   UI（书架/书源/阅读/设置/登录）
│   ├── desktopMain/              #   Desktop 平台入口 + KCEF 初始化 + WebView 组件
│   └── build.gradle.kts          #   org.jetbrains.compose 1.11.x + webview 依赖
└── docs/API.md                   # 前端走 HTTP/WS API（既有契约不变）
```

**两层职责**：
- **backend（引擎）**：无头 WebView 能力（`webJs` 书源、`AnalyzeUrl` WebView 分支、`JsExtensions.webView*`）——供规则引擎与 API 使用，**不依赖 Compose UI**（KCEF 可 offscreen 运行）
- **composeApp（前端）**：桌面 UI（阅读/书架/书源管理/登录）+ 可见 WebView（网页书源浏览器、网页登录）——通过 HTTP/WS 调 backend

## 5. 任务分解（Part 7，预计顺序）

| Task | 内容 | 验收 |
|---|---|---|
| T7.0 | 调研落地：确定 WebView 库版本组合（compose-webview 2.0.3 vs Compose 1.11.x 兼容矩阵；或退 KCEF 直连）；`backend` 引入 KCEF 依赖跑通 offscreen 最小加载 | 最小程序能 offscreen 加载 HTML 并 executeJavaScript 取回结果 |
| T7.1 | `WebViewRequestConfig` + `PooledWebView` 等价迁移（0 逻辑改动） | diff ≈ 0 |
| T7.2 | `WebViewPool` 桌面版（KCEF browser 复用池 + 清理协程） | 池容量/复用/超时清理冒烟 |
| T7.3 | `WebCacheManager` 接入已迁移 CacheManager + `WebJsExtensions` JS 桥（注入协议 + request 分发逐字保留） | JS 里 `window.legado.request('getStringAwait', ...)` 往返成功 |
| T7.4 | `BackstageWebView` 桌面版（无头加载 + sourceRegex + overrideUrlRegex + delayTime + timeout + cacheFirst） | 与 Android 版同参数同行为；`--webview-smoke-test` |
| T7.5 | 解除调用点裁剪：`AnalyzeRule.getWebJsResult`（@webjs:）、`AnalyzeUrl` useWebView 分支、`JsExtensions.webView/webViewGetSource/webViewGetOverrideUrl` | 原有规则/书源跑通（对照原版逻辑逐字恢复） |
| T7.6 | Compose MP 工程骨架（`composeApp` KMP + desktopMain），最小窗口 + 后端 health 状态显示 | `./gradlew :composeApp:run` 出窗口，调通 backend API |
| T7.7 | Compose 前端：书架/书源管理/阅读页（走 API.md） | 端到端：导入书源→搜索→阅读→进度 |
| T7.8 | 前端 WebView 集成（登录/网页书源）+ Part 7 联测 | 网页登录走通；`tools/test_backend.sh` 增加 webview 段全绿 |

## 6. 风险与对策

| 风险 | 对策 |
|---|---|
| KCEF bundle 体积大（~200MB）且首次要下载 | 用 JetBrains Runtime JDK 时可直接加载 bundled binary（compose-webview ≥1.9.40）；发行版可内置 jcef-bundle；Linux 发行版需 libgtk/glib 依赖 |
| compose-webview 2.0.3 基于 Compose 1.8.0，与 1.11.x 兼容性未知 | 先做 T7.0 兼容矩阵验证；备选 Wry（ComposeNativeWebview）；最坏退 KCEF 直连（无 Compose 层） |
| Android `addJavascriptInterface` 在桌面无等价物 | JS 桥用注入 `<script>` + `window.legadoJsBridgeResult` 回调 + CacheManager 内存缓存（协议与原版一致，JS 侧书源无需改动） |
| 无头 WebView 需要事件循环 | KCEF 需运行在带消息泵的线程；BackstageWebView 的 suspend 用 `suspendCancellableCoroutine` + UI 线程调度等价实现 |
| 引擎与前端模块依赖冲突 | `backend` 保持纯 JVM（不引入 Compose）；WebView 依赖只进 `composeApp`/专用模块；无头执行若需 KCEF 则放独立模块隔离 |
| iOS/Web target 不可行（依赖 JVM） | 计划明确只启用 Desktop(JVM)；KMP 结构预留，未来引擎替换后再扩 |

## 7. 已确认决策（2026-08-10 用户拍板）

1. **WebView 库选型**：**KCEF**（compose-webview-multiplatform）优先，能力对齐 Android WebView
2. **前端范围**：**最小可用**（书架/书源/阅读核心闭环），后续逐步完善
3. **实施顺序**：**分两步走** —— 先引擎层（T7.1~T7.5，无头 WebView 兼容，不依赖 Compose UI），再做前端（T7.6~T7.8）
4. **Compose 版本**：**稳定版**（1.11.x，不用 beta）
5. **网页登录**（登录 UI v2 之外的原版 WebView 登录）：**最小可用阶段不包含**。过渡方案（已确认）：
   - 引擎/前端提供 API：返回书源登录 URL
   - 用户在**系统浏览器**中完成登录
   - 登录后**手动将 Cookie 填入设置**（前端设置页表单），存回 `CookieStore`
   - 内嵌 WebView 自动登录、自动回传 Cookie 归入**后续完善**（T7.8+ 前端 WebView 集成阶段）

## 8. 附：调研链接

- compose-webview-multiplatform（KCEF 路线）：https://github.com/KevinnZou/compose-webview-multiplatform（README.desktop.md）
- ComposeNativeWebview（Wry 路线）：https://github.com/kdroidFilter/ComposeNativeWebview
- KCEF：https://github.com/DatL4g/KCEF
- Compose Multiplatform releases：https://github.com/JetBrains/compose-multiplatform/releases（1.11.1 稳定）
- Maven Central：io.github.kevinnzou:compose-webview-multiplatform:2.0.3
