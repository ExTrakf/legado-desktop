# Legado Desktop 移植规划表（详细版）

> 配套状态追踪：`../STATUS.json`（每轮必须同步更新）
> 原则：改错不改逻辑（Android→JVM 等价替换）；跨平台（Linux/macOS/Windows）；每 Task 测、每 Part 联测

## 0. 技术栈与架构

- 后端：Kotlin 2.4.10 + Gradle 8.14.4 + sqlite-jdbc + OkHttp + NanoHTTPD + Rhino（htmlunit-core-js fork）
- 数据层：SQLite schema v99（对齐 Legado Room），24 实体表 + 1 视图（book_sources_part）
- 服务：NanoHTTPD HTTP + WebSocket + Ktor MCP（127.0.0.1:2323，CORS 全开）
- 运行：`installDist` 直接脚本（5 秒就绪，绕开 Gradle daemon）

## 1. Part 依赖图

```
Part 0 基础与构建（✅ DONE）
  └─> Part 1 数据层（✅ DONE：24/24 DAO + 冒烟全过）
        └─> Part 2 配置与网络层（✅ DONE：--net-smoke-test 16 项断言全过）
              └─> Part 3 规则引擎（AnalyzeRule + Rhino）
                    └─> Part 4 书源与读书引擎（SourceHelp/jsSource/WebBook）
                          └─> Part 5 API 层（HTTP/WS/MCP 控制器）
                                └─> Part 6 本地书籍/图片/MCP/备份（可选增强）
```

依赖规则：Part N 必须完整验收后进入 Part N+1；每个 Task 完成后立即测试。

## 2. Part 1 数据层详细任务（当前进行中）

### 已实现（T1.1 ✅，集成测试通过）

| DAO | 方法数 | 状态 |
|---|---|---|
| BookDao | 60 | ✅ 已实现+验证 |
| BookChapterDao | 13 | ✅ 已实现+验证 |
| BookSourceDao | 70 | ✅ 已实现+验证 |

### 待实现（21 个，约 250 个方法）

**T1.2 书源/规则类**（依赖：实体已迁移；验收：CRUD+规则字段 JSON 存取）
| DAO | 方法数 | 对应表 | 实现要点 |
|---|---|---|---|
| ReplaceRuleDao | 29 | replace_rules | 启用/停用、测试、分组 |
| TxtTocRuleDao | 12 | txtTocRules | 默认规则导入（deleteDefault/insert） |
| RuleSubDao | 7 | ruleSubs | 规则子项 CRUD |
| DictRuleDao | 7 | dictRules | 默认规则导入 |
| HighlightRuleDao | 11 | highlightRules | 高亮规则 CRUD |

**T1.3 书籍类**（验收：CRUD + 级联 + 进度）
| DAO | 方法数 | 对应表 | 实现要点 |
|---|---|---|---|
| BookGroupDao | 17 | book_groups | 内置分组常量、show/idsSum/排序 |
| BookmarkDao | 9 | bookmarks | 书签 CRUD |
| SearchBookDao | 13 | searchBooks | 搜索缓存（getByKey/insert/delete） |
| ReadRecordDao | 15 | readRecord | 阅读记录/统计 |
| CacheDao | 6 | caches | 缓存（含 SourceVariables 清理） |
| CookieDao | 6 | cookies | Cookie 持久化（增删改查） |
| BookHighlightDao | 10 | highlights | 划线高亮 |

**T1.4 RSS 类**（验收：CRUD）
| DAO | 方法数 | 对应表 | 实现要点 |
|---|---|---|---|
| RssSourceDao | 35 | rssSources | 源管理（enabled/分组/排序） |
| RssArticleDao | 9 | rssArticles | 文章 CRUD |
| RssReadRecordDao | 9 | rssReadRecords | 阅读记录 |
| RssStarDao | 12 | rssStars | 收藏 |
| SearchKeywordDao | 9 | search_keywords | 搜索词记录 |

**T1.5 其他 + Part1 联测**（验收：全部 DAO 在临时库全量 CRUD 冒烟）
| DAO | 方法数 | 对应表 | 实现要点 |
|---|---|---|---|
| ServerDao | 8 | servers | WebDAV 服务器配置 |
| HttpTTSDao | 9 | httpTTS | TTS 配置（不移植功能，仅数据） |
| AutoTaskRuleDao | 11 | auto_task_rules | 定时任务规则 |
| KeyboardAssistsDao | 9 | keyboardAssists | 键盘辅助配置 |

**T1.5 联测标准**：
- 临时库初始化（LEGADO_DESKTOP_HOME 指向 /tmp）
- 24 个 DAO 全部实例化成功（appDb.init 无异常）
- 每个 DAO 至少 1 次 insert/query/update/delete 冒烟
- 集成测试脚本 `tools/test_backend.sh` 扩展 DAO 冒烟段

## 3. Part 2 配置与网络层

| Task | 内容 | 验收 |
|---|---|---|
| T2.1 | 配置系统：AppConfig/LocalConfig/SourceConfig 已改 JSON 版（DesktopEnv），补验收 | 读写偏好、重启保持（config.json） |
| T2.2 | HTTP 客户端：OkHttpUtils/HttpHelper/StrResponse/SSLHelper/DecompressInterceptor/HttpLog 已迁移 | https 请求返回 StrResponse；gzip/brotli 解压正确 |
| T2.3 | Cookie：CookieStore/CookieManager（纯 HTTP 版，已去掉 webkit） | Cookie 持久化到 cookies 表，重启可用 |
| T2.4 | 代理：HttpProxyConfig/Socks5Proxy + 联测 | 设置代理后请求走代理 |

## 4. Part 3 规则引擎

| Task | 内容 | 验收 |
|---|---|---|
| T3.1 | AnalyzeRule 基础（RuleData/解析器：JSoup/XPath/JSONPath/Regex，已迁移） | 用 Legado 文档样例规则解析 HTML/JSON |
| T3.2 | AnalyzeUrl（已迁移，WebView/Cronet 分支已裁剪为 okhttp） | 构造带 key/page 的搜索 URL 并请求 |
| T3.3 | Rhino 集成（模块已迁，JS 环境/JsExtensions 桌面版绑定已裁剪） | 执行 JS 源 mainJs + java 绑定调用 |
| T3.4 | Part3 联测 | 纯 JS 源 + 规则源各跑通一次 |

注意：JS 源的 WebView 绑定（webView 系列）已抛错标注；登录 UI v2 仅数据解析（LoginUiV2 已迁移）。

## 5. Part 4 书源与读书引擎

| Task | 内容 | 验收 |
|---|---|---|
| T4.1 | SourceHelp 书源管理（已迁移，UI 回调已裁剪） | 导入/删除/启用书源 |
| T4.2 | jsSource（JsSourceEngine/JsSourceBook/JsSourceUpsert 已迁移） | JS 源搜索/目录/正文 |
| T4.3 | WebBook（已迁移）+ BookHelp/ContentProcessor（图片解密已标注 T6.2） | 真实书源：搜索→目录→正文全链路 |
| T4.4 | Part4 联测 | 多源搜索合并去重、阅读进度存取 |

## 6. Part 5 API 层

| Task | 内容 | 验收 |
|---|---|---|
| T5.1 | HTTP 路由整合（移植 HttpServer，挂控制器；当前 web/ 已迁移待接入） | 路由表与 api.md 一致 |
| T5.2 | 书源/RSS/替换规则 API（BookSourceController/ReplaceRuleController/RssSourceController 已迁移） | curl 增删改查全通过 |
| T5.3 | 书籍 API（BookController 已迁移，Glide 封面裁剪 T6.2） | curl 书架/目录/正文/进度 |
| T5.4 | WebSocket（searchBook/bookSourceDebug/rssSourceDebug 已迁移） | WS 客户端收到结果流 |
| T5.5 | Part5 端到端联测 | 导入源→搜索→加书架→目录→正文→进度 |

## 7. Part 6 附加（可选）

| Task | 内容 | 验收 |
|---|---|---|
| T6.1 | 本地书籍解析（localBook 已暂删，重新迁移：TXT/EPUB/MOBI/UMD） | 导入 txt/epub 可读 |
| T6.2 | 封面/图片接口（ImageUtils 解密 + BookCover 恢复） | cover/image 返回字节 |
| T6.3 | MCP 服务（web/mcp 已迁移，T5 后接入） | MCP 工具列表可枚举 |
| T6.4 | 备份/导入兼容（storage/Backup 等已暂删，重新迁移） | 导入 Legado 备份 JSON |

## 7.5 Part 7 WebView 兼容 + Compose Multiplatform 前端（规划中）

> 详细方案（调研结论/方案对比/任务分解/风险）：**`docs/WEBVIEW-COMPOSE-PLAN.md`**
> 状态：2026-08-10 调研完成，待确认决策点（WebView 库选型 / 前端范围 / 实施顺序）

| Task | 内容 | 验收 |
|---|---|---|
| T7.0 | WebView 库兼容矩阵验证（KCEF vs Wry；Compose 版本） | offscreen 最小加载 + executeJavaScript 取回结果 |
| T7.1 | WebViewRequestConfig + PooledWebView 等价迁移 | diff ≈ 0 |
| T7.2 | WebViewPool 桌面版（KCEF browser 复用池） | 池容量/复用/超时清理冒烟 |
| T7.3 | WebCacheManager + WebJsExtensions JS 桥（request 分发逐字保留） | JS 桥往返成功 |
| T7.4 | BackstageWebView 桌面版（无头加载 + sourceRegex/overrideUrlRegex/delayTime/timeout） | --webview-smoke-test |
| T7.5 | 解除调用点裁剪（AnalyzeRule webJs / AnalyzeUrl useWebView / JsExtensions.webView*） | 原书源跑通 |
| T7.6 | Compose MP 工程骨架（composeApp KMP + desktopMain） | run 出窗口 + 调通 backend API |
| T7.7 | Compose 前端（书架/书源/阅读，走 API.md） | 端到端阅读闭环 |
| T7.8 | 前端 WebView 集成 + Part 7 联测 | 网页登录走通；test_backend.sh 全绿 |

**约束重申**：只启用 Desktop (JVM) target（依赖全部 JVM 专属，iOS/Web 不可行）；backend 引擎保持纯 JVM 无 Compose 依赖；WebView 功能忠于原版逐组件等价迁移。

## 8. 跨平台检查清单（每个 Task 验收必查）

- [ ] 路径：只用 `File.separator`/`Paths`/`Path`，无硬编码 `/` 或 `\`
- [ ] JDBC URL：反斜杠已转正斜杠（SqliteDatabase 已处理）
- [ ] 资源读取：classpath 恒用 `/`（如 `/defaultData/xxx.json`）
- [ ] 换行/编码：UTF-8，无 Windows 专属 API
- [ ] 无 `android.*`/`androidx.*`/`splitties` import
- [ ] 逻辑等价：只做 Android→JVM 替换，不改业务逻辑

## 9. 测试策略

- **单 Task**：`./gradlew compileKotlin` 0 错误 + 该 DAO/功能的最小验证
- **单 Part**：`tools/test_backend.sh` 集成测试（启动→health→schema→CRUD→级联→清理）
- **注意**：workspace_shell 独立终端，启动服务+测试必须合并单条命令；结束精确 kill 自己启动的 PID
