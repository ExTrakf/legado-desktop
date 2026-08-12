# 未完成移植项清单（真缺口）

> 来源：2026-08-11 全量复核（对比原版 `E:\repos\legado` commit 36d58eea 与桌面版 backend 业务代码）
> 复核方法：目录/文件级 diff（排除 ui/service/receiver/base 与前端）、匹配文件行数比筛查、
> stub/裁剪标记扫描（89 处命中）、24 DAO 方法名级核对、资源文件对比、关键引用链追查
> **更新（2026-08-12）：第 2/3/5/6 项已补全并全量回归通过；第 1/4 项用户明确不做（AutoTask/WebDAV）**
> 关联：STATUS.json（当前状态）｜PLAN.md（Part 规划）｜HANDOVER.md（会话经验）

---

## 1. 定时自动任务引擎（AutoTask）

> **2026-08-12 用户明确不做（"Auto task不做"）**。DAO/实体/CronSchedule 已迁移（数据层完整）；
> 执行引擎（model/AutoTask*）不移植。

- **原版文件**：`model/AutoTask.kt`、`model/AutoTaskRunner.kt`、`model/AutoTaskProtocol.kt`、`model/AutoTaskSchedulePolicy.kt`
- **现状**：4 个文件未迁移。已迁移的只有数据侧：`AutoTaskRuleDao`/`Impl`、`AutoTaskRule` 实体、
  `utils/CronSchedule.kt`（cron 解析器）。`model/AutoTask.kt` 提供 `normalizeScript`、`buildBookUpdateTask`
  （书架定时更新任务生成）、`BOOK_UPDATE_GENERATOR` 等纯逻辑，桌面版**完全没有运行入口**
- **原版依赖**：`service/AutoTaskScheduler`（Android Service 调度 cron）+ appCtx
- **迁移建议**（如需启用）：调度器用桌面协程等价（参照 `CheckSourceRunner` 模式把 Service 迁成协程 Runner）；
  纯逻辑（AutoTask/AutoTaskProtocol/SchedulePolicy）逐字迁移；需暴露 API 触发（原版由 UI 触发）
- **优先级**：~~中~~ → 不做

## 2. 缓存书籍（CacheBook，批量缓存章节正文）

> **2026-08-12 已补全**：`model/CacheBook.kt` 等价迁移（Service→协程 Job 直接驱动，ReadBook 阅读状态裁剪）
> + API `POST /cacheBook`（{bookUrl,start,end}）、`/cacheBookStop`、`/cacheBookRemove`；全量回归通过。

- **原版文件**：`model/CacheBook.kt`
- **原版依赖**：`CacheBookService`（Android 前台服务）→ 桌面协程 `ensureProcess()` + `startProcessJob`
- **迁移建议**：正文抓取/落盘逻辑逐字保留（download/downloadAwait/重试3次/onEachParallel）
- **优先级**：~~中~~ → done

## 3. 文件下载（DownloadService）

> **2026-08-12 已补全**：`model/Download.kt` 由抛错 stub → OkHttp 流式下载（AnalyzeUrl.getInputStreamAwait）
> 到 `<数据目录>/cache/downloads`（原 Android DownloadManager 下载到系统 Downloads）。

- **原版文件**：`model/Download.kt` + `service/DownloadService.kt`（Android 前台下载）
- **现状**：~~抛错 stub，桌面无调用方~~ → 已实现 `Download.start(url, fileName)`（fileName 做路径穿越防护）
- **优先级**：~~低~~ → done

## 4. WebDAV（可选，plan 已标注）

> **2026-08-12 用户明确不做（"Web dav不做"）**。已迁移的仅有间接数据：`Server` 实体/DAO、
> `Backup` 的 servers AES 加密、`BookController` 中 `AppWebDav.uploadBookProgress` 注释占位、
> `LocalBook` WebDAV 自动恢复 → false。

- **原版文件**：`help/AppWebDav.kt`、`lib/webdav/`（WebDav/WebDavFile/Authorization/WebDavException）、
  `model/remote/`（RemoteBook/RemoteBookManager/RemoteBookWebDav）
- **优先级**：~~低~~ → 不做

## 5. rar/7z/密码 zip 解压（LibArchiveUtils）

> **2026-08-12 已补全**：原版 Android libarchive-JNI（me.zhanghai.android.libarchive）不可移植 →
> 改 **commons-compress（7z/zip）+ junrar（rar3）**；`ArchiveUtils.deCompress/getArchiveFilesName` 按扩展名分发
> （zip JDK / 7z SevenZFile / rar junrar，统一 zip-slip 防护）；`JsExtensions.getRarByteArrayContent/get7zByteArrayContent`
> 接入新实现；`read7zEntryBytes/readRarEntryBytes` 供 JS 源按路径读取条目。7z 创建/读取/deCompress + zip 回归已验证 PASS。

- **原版文件**：`utils/compress/LibArchiveUtils.kt`（libarchive 原生绑定，支持 7z/rar/密码 zip）
- **现状**：~~仅 zip~~ → 7z/rar/zip 全支持（密码 zip 未支持，原版也仅经 libarchive 原生）
- **优先级**：~~低~~ → done

## 6. 默认数据导入（DefaultData 死代码 + keyboardAssists 不 seed）

> **2026-08-12 已补全**：`appDb.init()` 首次建库若 `keyboardAssists` 为空则 seed（对齐原版 AppDatabase onOpen，
> 已验证 32 行）；`DefaultData.importDefault*`（原版 UI"恢复默认"触发）暴露为
> `POST /restoreDefaultData`（body {"types":[...]} 可选，缺省全部；已验证 txtTocRules 26/dictRules 5/rssSources 4/httpTTS 3）。
> 另修 `AppConfig.defaultBookTreeUri` 空白回退 bug（getPrefString 空串 → takeIf isNotBlank，本地书存 CWD 问题）。

- **原版行为**：AppDatabase（Room onCreate/onOpen）建库时自动 seed keyboardAssists；importDefault* 由 UI 触发
- **现状**：~~死代码 + 不 seed~~ → seed + API 全通
- **优先级**：~~中~~ → done

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
静态资源伺服（AssetsWeb）、WebView 登录 UI、**AutoTask 定时任务引擎、WebDAV（2026-08-12 用户拍板不做）**。
（完整清单见 README「明确不移植」与 STATUS.json `constraints.notDoing`）

---

## 9. 执行记录

- ✅ 2026-08-12：默认数据导入（item 6）、CacheBook（item 2）、Download（item 3）、rar/7z（item 5）全部完成；
  全量回归 7 冒烟（dao25/net16/rule23/src18/api29/local11/webview15=137 断言）全绿 + 服务级校验通过
- ⛔ 2026-08-12：AutoTask（item 1）、WebDAV（item 4）用户明确不做，从执行列表移除

> 每项遵循既有纪律：忠于原版等价迁移（改错不改逻辑）、每 Task 测试通过才置 done、
> 冒烟输出 ASCII [PASS]/[FAIL]/[SKIP]（教训28）、完成后同步 STATUS.json/HANDOVER.md。
