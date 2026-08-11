# 未完成移植项清单（真缺口）

> 来源：2026-08-11 全量复核（对比原版 `E:\repos\legado` commit 36d58eea 与桌面版 backend 业务代码）
> 复核方法：目录/文件级 diff（排除 ui/service/receiver/base 与前端）、匹配文件行数比筛查、
> stub/裁剪标记扫描（89 处命中）、24 DAO 方法名级核对、资源文件对比、关键引用链追查
> 结论：**下列 6 项是真实未完成移植（不在"明确不移植"清单内）**；其余 209 个缺失文件均为
> Android UI 专属或已文档化裁剪（见本文第 4 节）
> 关联：STATUS.json（当前状态）｜PLAN.md（Part 规划）｜HANDOVER.md（会话经验）

---

## 1. 定时自动任务引擎（AutoTask）

- **原版文件**：`model/AutoTask.kt`、`model/AutoTaskRunner.kt`、`model/AutoTaskProtocol.kt`、`model/AutoTaskSchedulePolicy.kt`
- **现状**：4 个文件未迁移。已迁移的只有数据侧：`AutoTaskRuleDao`/`Impl`、`AutoTaskRule` 实体、
  `utils/CronSchedule.kt`（cron 解析器）。`model/AutoTask.kt` 提供 `normalizeScript`、`buildBookUpdateTask`
  （书架定时更新任务生成）、`BOOK_UPDATE_GENERATOR` 等纯逻辑，桌面版**完全没有运行入口**
- **原版依赖**：`service/AutoTaskScheduler`（Android Service 调度 cron）+ appCtx
- **迁移建议**：调度器用桌面协程等价（参照 `CheckSourceRunner` 模式把 Service 迁成协程 Runner）；
  纯逻辑（AutoTask/AutoTaskProtocol/SchedulePolicy）逐字迁移；需暴露 API 触发（原版由 UI 触发）
- **优先级**：中（书架定时更新 / 定时任务属于核心自动化能力，但依赖前端入口）

## 2. 缓存书籍（CacheBook，批量缓存章节正文）

- **原版文件**：`model/CacheBook.kt`
- **现状**：未迁移。原版 `CacheBook` 用 `CompositeCoroutine` 并行抓取 Book/BookChapter 正文，
  经 `BookHelp.saveContent` 落盘，进度走 `CacheBookService`（Android 前台服务）
- **迁移建议**：Service 依赖裁剪成协程 Runner（同 CheckSourceRunner 模式），正文抓取/落盘逻辑逐字保留
- **优先级**：中（离线缓存是阅读核心能力之一，但阅读正文本身已可用）

## 3. 文件下载（DownloadService）

- **原版文件**：`model/Download.kt` + `service/DownloadService.kt`（Android 前台下载）
- **现状**：`model/Download.kt` 已迁但是**抛错 stub**（`NoStackTraceException("桌面版下载服务尚未实现")`）。
  目前桌面**无任何调用方**（原版调用方全部在 UI：UpdateDialog/WebViewActivity/ReadRssActivity/BottomWebViewDialog），
  属潜在 stub，不影响现有链路
- **迁移建议**：下载用 OkHttp 流式写文件 + 进度回调等价实现；接入点按需（如正文/附件下载 API）
- **优先级**：低（当前无调用方）

## 4. WebDAV（可选，plan 已标注）

- **原版文件**：`help/AppWebDav.kt`、`lib/webdav/`（WebDav/WebDavFile/Authorization/WebDavException）、
  `model/remote/`（RemoteBook/RemoteBookManager/RemoteBookWebDav）
- **现状**：本体全部未迁移。已迁移的仅有间接数据：`Server` 实体/DAO、`Backup` 的 servers AES 加密、
  `BookController` 中 `AppWebDav.uploadBookProgress` 注释占位、`LocalBook` WebDAV 自动恢复 → false
- **迁移建议**：`lib/webdav/` 纯 Java 客户端可逐字迁移；`AppWebDav`（进度/书架同步）与
  `model/remote/`（RemoteBook 书架备份）为可选增强
- **优先级**：低（明确可选，原 PLAN 已把 T6.4 WebDAV 划入可选，随后删除）

## 5. rar/7z/密码 zip 解压（LibArchiveUtils）

- **原版文件**：`utils/compress/LibArchiveUtils.kt`（libarchive 原生绑定，支持 7z/rar/密码 zip）
- **现状**：未迁移。桌面 `utils/ArchiveUtils.kt` 与 `help/JsExtensions.kt`（压缩包内文件读取）仅支持
  **zip**，注释标注"初版支持 zip；rar/7z 后续引入 commons-compress"、"桌面版暂支持 zip"
- **迁移建议**：引入 `commons-compress`（7z/rar 读 + 密码 zip）等价实现，或接入原生 libarchive
- **优先级**：低（rar/7z 本地压缩包阅读为小众场景）

## 6. 默认数据导入（DefaultData 死代码 + keyboardAssists 不 seed）

- **原版行为**：
  - `DefaultData.importDefaultHttpTTS/importDefaultTocRules/importDefaultRssSources/importDefaultDictRules`
    由 UI ViewModel 的"恢复默认"按钮触发
  - `data/AppDatabase.kt`（Room onCreate）建库时自动 seed `keyboardAssists` 表
- **现状**：
  - 桌面 `help/DefaultData.kt` 的 `importDefault*` 函数已迁但**无任何调用方**（死代码）
  - 桌面 `SqliteDatabase.init` 只执行 schema.sql，**不 seed keyboardAssists** → 该表恒空
  - 注：`defaultData/bookSources.json` 原版同样无人引用（遗留资源，非缺口）
- **迁移建议**：`appDb.init()` 首次建库（表空时）调用 `DefaultData.importDefault*` + seed keyboardAssists；
  另需暴露"恢复默认"API（原版是 UI 触发）
- **优先级**：中（首次运行体验 + 词典/目录规则默认值；功能缺失但可用）

---

## 7. 已核对无缺口（复核通过项）

- **数据层**：24/24 DAO 全部方法齐备（接口默认方法 + Impl，方法名级核对零缺失）；
  44 实体对应 schema.sql v99；`--dao-smoke-test` 25 断言绿
- **资源**：`defaultData/` 10 个 JSON 与桌面完全一致；`scripts/cryptojs.min.js` 已迁；
  仅缺 `scripts/beautify.min.js`，但**原版同样无人引用**（遗留资源）
- **匹配文件行数**：显著变短的仅限文档化裁剪项（BitmapUtils/FileUtils/ImageProvider/BookCover/
  PdfFile/Backup/Restore/DAO 接口去 @Query SQL 注解等），无静默截断的业务逻辑
- **Part 0~7 引擎层**：忠于原版 diff 结论见 HANDOVER（逐字/等价替换均已记录）

## 8. 明确不移植（简要）

TTS/朗读/音频（help/audio、AudioPlay、ReadAloud、exoplayer）、视频/弹幕/漫画（gsyVideo、VideoPlay、
ReadManga）、Glide 图片加载（help/glide）、Cronet（lib/cronet、help/http/Cronet）、应用更新/Firebase
（help/update、CrashHandler）、Android UI 全套（通知/前台服务、lib/permission、lib/prefs、lib/theme、
lib/dialogs、ShortCuts、ReaderProvider、CanvasRecorder）、MCP、评论 API（ReviewController）、
静态资源伺服（AssetsWeb）、WebView 登录 UI。
（完整清单见 README「明确不移植」与 STATUS.json `constraints.notDoing`）

---

## 9. 建议执行顺序（供后续会话参考）

1. **默认数据导入**（第 6 项，小、低风险，独立可验收：首次建库 seed + importDefault 调用 + 冒烟断言）
2. **定时自动任务引擎**（第 1 项，中：AutoTask/AutoTaskProtocol/SchedulePolicy 逐字 + AutoTaskRunner 协程化 + API）
3. **缓存书籍**（第 2 项，中：CacheBook 协程化 + API）
4. **WebDAV / rar7z / Download**（第 4/5/3 项，低，可选）

> 每项遵循既有纪律：忠于原版等价迁移（改错不改逻辑）、每 Task 测试通过才置 done、
> 冒烟输出 ASCII [PASS]/[FAIL]/[SKIP]（教训28）、完成后同步 STATUS.json/HANDOVER.md。
