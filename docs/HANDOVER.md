# 交接文档（给下一个会话）

> 项目：Legado（开源阅读）后端引擎桌面移植 → 纯 JVM Kotlin + SQLite + OkHttp + Rhino
> 仓库：https://github.com/ExTrakf/legado-desktop（公开，main，身份 Maicy0609）
> 状态追踪：`../STATUS.json`（现在在哪）｜规划：`../docs/PLAN.md`（接下来做什么）｜本文件：经验与坑

---

## 1. 用户硬约束（违反 = 返工）

1. **改错不改逻辑**：只做 Android→JVM 等价替换；修编译错误时绝不借机改业务逻辑。不确定的功能裁剪要注释标注（"原 X，T6.x 实现"）。
2. **跨平台**（Linux/macOS/Windows，不管 Android）：
   - 路径只用 `File.separator` / `Paths.get` / `File(root, name)` 拼接，禁止硬编码 `/` 或 `\`
   - JDBC URL 反斜杠要转正斜杠（`SqliteDatabase.kt` 已处理，新写数据库代码照抄）
   - classpath 资源恒用 `/`（`getResourceAsStream("/defaultData/xxx.json")`）
3. **前端分离**：后端只出 HTTP/WS API（CORS 全开），前端用户自建（官方 `modules/web` 不用）。
4. **明确不移植**（已裁剪，别以为丢了）：TTS/音频、视频/弹幕、Android UI/通知/前台服务/广播/ContentProvider、WebView 一切、Firebase/CrashHandler、Glide。
5. **任务制**：每 Task 完成必须测试通过才置 done；每 Part 完成必须联测；每轮结束同步更新 `STATUS.json`。
6. **单次 shell 调用内完成「启动→测试→清理」**（workspace_shell 是独立终端，响应结束后后台进程被释放）；结束要**精确 kill 自己启动的 PID**（不要 `pkill -f` 乱杀）。

---

## 2. 环境速查

- Java 17 已装；Gradle wrapper 8.14.4（腾讯镜像）+ Maven 依赖（阿里云镜像）已配好
- 构建：`cd backend && export GRADLE_USER_HOME=/workspace/.gradle && ./gradlew build -x test`
- **运行测试**：`./gradlew installDist` 后用 `build/install/legado-desktop-backend/bin/legado-desktop-backend` 直接启动（5 秒就绪；Gradle daemon 启动要 3-5 分钟，别等它）
- 集成测试：`bash tools/test_backend.sh`（自动启动→health→schema→CRUD→级联→清理）
- 数据目录：`LEGADO_DESKTOP_HOME=/tmp/legado-test`（测试用）

---

## 3. 踩过的坑（重点，按类别）

### A. Gradle / 构建
- daemon 慢：测试一律走 installDist 直接脚本，不要 `gradlew run &`
- 镜像配置在 `backend/settings.gradle.kts`（aliyun 第一优先）和 `gradle/wrapper/gradle-wrapper.properties`（tencent）
- `org.json:json` 依赖已加（Server 实体用 JSONObject）

### B. SQLite / Room schema
- **sqlite-jdbc 的 `execute()` 不支持多语句**：schema.sql 必须按 `;` 拆分逐条执行（`SqliteDatabase.kt` 已处理）
- **Room schema JSON（`/workspace/legado/app/schemas/.../99.json`）的 `createSql` 无结尾分号**：生成 schema.sql 时每条补 `;`（已处理；若重新生成要照做）
- **表名以 Room `@Entity(tableName=...)` 实际值为准**（驼峰！`rssSources`/`caches`/`searchBooks`/`readRecord`/`httpTTS`/`ruleSubs`…），不要臆测下划线
- `book_sources_part` 是 `@DatabaseView`（不在 99.json 的 entities 里），CREATE VIEW 需从 `BookSourcePart.kt` 的注解手动提取（已写入 schema.sql）
- 外键级联测试：python sqlite3 连接默认关 `PRAGMA foreign_keys`，测试要先开
- DAO 接口里跨行 `@Query` 注解清理：用**括号配对的 python 脚本**（perl 单行正则清不干净）

### C. Kotlin 迁移（最容易翻车）
- **别大爆炸迁移**：一次性迁 300+ 文件导致 8208 个错误。按依赖闭包小步迁移。
- **sed/perl 机械替换易错**：
  - `s/appCtx\.getPref/GetPref/` 会把 getPref 变 GetPref（大写）→ 编译报 Unresolved `GetPrefXxx`，需全局反向修正
  - `TextUtils.join(",", it)` 不能替换成 `joinToString(",", it)`（参数错位，应为 `it.joinToString(",")`）
  - perl `-i` 在 `$(grep ...)` 无输出时报错 `-i used with no filenames`
- **删除函数块要用括号配对**（`{`/`}` depth 计数），`re.sub` 按行删容易误删（HttpHelper 的 Cronet 裁剪曾把 `}` 删坏，导致函数结构断裂）
- **多行 `@Entity(...)` 注解**：migrate.sh 删了 `@Entity(` 行但残留参数行（`tableName = "..."` 到 `)`），要按「tableName 开头 → `)` 行」整体删
- **注解残留全家桶**（都要清）：`@get:Ignore`、`@delegate:Ignore`、缩进版 `@Ignore`、`@SuppressLint`（全局转 `@Suppress`，注意同名重复要合并）、`@Keep`、`@IntDef`、`@Parcelize`、`@DatabaseView`、`@Upsert`、`@Type`、`@RequiresApi`、`@get:Query` 后的 `@get` 残留
- **JVM 签名冲突**：两个扩展函数同名同参（`File.getFile` 与顶层 `getFile`）→ `Platform declaration clash`，删一个
- **`import kotlinx.coroutines.flow.emit` 不合法**（emit 是 FlowCollector 成员，flow{} 块内直接用，无需 import）
- **接口默认实现**：DAO 接口里已有默认方法（`flowByGroup`/`dealGroups`/`updatePreservingReadConfig` 等），**不要重复 override**（会引用接口 private 的 dealGroups 编译失败）
- **DAO 写操作返回类型**：接口是 `fun xxx(...)`（Unit），Impl 里 `= withLock { db.execute(...) }` 返回 Int → 类型不匹配。**查询用 `= withLock { ... }`，写操作用语句体 `{ withLock { db.execute(...) } }`**（BookDaoImpl 等已按此规范，新 DAO 照抄）

### D. Android API 等价替换模式（已建立，照用）
| Android | 桌面版 | 备注 |
|---|---|---|
| `appCtx` | `DesktopEnv`（homeDir/config.json） | 全局单例 |
| `appCtx.getPrefXxx/putPrefXxx` | 顶层函数 `getPrefXxx/putPrefXxx`（utils/PreferencesExtensions） | putPrefString 接受 String?（null=移除） |
| `SharedPreferences("local")` | DesktopEnv + 前缀 `local_` | LocalConfig 已改 |
| `TextUtils.isEmpty(x)` | `x.isNullOrEmpty()` | |
| `android.util.Base64` | `java.util.Base64` | **flags 语义要实现**（NO_WRAP/URL_SAFE/NO_PADDING），JS 书源会传 flags！`EncoderUtils.kt` 已完整实现 |
| Room DAO | 手写 Impl + `SqlExecutor`（反射映射/`?` 参数绑定/`IN (:list)` 展开） | SQL 从 `dao-sql.json` 对照 |
| `android.webkit.*` | 裁剪（Cookie/WebView 桌面版无） | |
| `XmlPullParser`（RSS） | javax.xml DOM（`RssParserDefault` 已重写，localName 匹配 content:encoded） | |
| `assets.open(...)` | `getResourceAsStream("/...")` + 复制 assets → resources/ | 路径恒 `/` |
| `isMainThread` | 恒 `false`（utils/ThreadExtensions） | |
| `File.exists(vararg)` | 桌面版扩展（DesktopExtensions） | 注意 JVM 签名冲突 |
| `AppLog.put(msg, e, toast)` | 3 参签名已兼容 | |
| `DebugLog`/`postEvent` | 桌面版 stub（保留调用点） | |

### E. 已裁剪功能（后续 Part 要恢复的）
- `model/localBook/*`（T6.1 重新迁移：TXT/EPUB/MOBI/UMD）
- `help/storage/Backup*`、`ImportOldData`（T6.4）
- `web/mcp/*`（T6.3）
- `model/BookCover`、`ImageProvider`、`help/ImageUtils`（T6.2，图片解密）
- `model/login/*` 除 `LoginUiV2`（纯逻辑已迁）
- `model/remote/*`（WebDAV，可选）
- JsExtensions 里 webView/openVideoPlayer/showBrowser/getThemeConfig 已裁剪为抛错/空值（WebView/视频不移植）

---

## 4. 当前状态快照（2026-08-10）

- **Part 0 ✅ DONE**：编译 0 错误（8208→0，约 420 文件）、集成测试全过、installDist 5s 就绪
- **Part 1 ✅ DONE**：基础设施（SqliteDatabase/SqlExecutor/appDb/schema v99=24表+1视图）+ **24/24 DAO 全部实现**（T1.2~T1.5 完成）
  - 新增 `--dao-smoke-test` 入口（Main → DaoSmokeTest）：24 DAO 全量 CRUD + flow + collate localized + IN(:list) + 外键，25 项断言，跑完退出码汇总
  - `tools/test_backend.sh` 已集成 DAO 冒烟段（4.5 节），集成测试全绿
- 本轮修复的基础设施坑（详见教训 8/9/10）：
  - `SqlExecutor.bind()` 只处理 `:name` 不处理字面 `?` → 所有 `?` 风格 DAO 参数不绑定、查询静默空返回；已修（`?` 与 `:name` 共用位置顺序计数器）
  - `queryList(..., String::class.java)` 反射实例化失败（标量列表查询）；已修（基础类型取第一列）
  - `collate localized`（Android 专属）桌面 SQLite 不存在；已用 `org.sqlite.Collation.create` 注册（大小写不敏感）
  - 实体 ReadRecordBook/ReadRecordShow/RssReadRecord/KeyboardAssist 补默认值（反射无参构造适配）

## 5. 下一步（按 PLAN.md）

1. **Part 2 配置与网络层**：T2.1 配置系统验收（AppConfig/LocalConfig/SourceConfig 已改 JSON 版）→ T2.2 HTTP 客户端（OkHttp/StrResponse/SSL/解压）→ T2.3 Cookie 存储（CookieStore/CookieManager）→ T2.4 代理 + 联测
2. Part 3 规则引擎（AnalyzeRule + Rhino）…（PLAN.md 已列）

**实现 DAO 的方法**（照 BookDaoImpl 模式）：
- SQL 对照 `backend/src/main/resources/dao-sql.json`（已从 legado 提取 272 条）+ 原文件 `/workspace/legado/app/src/main/java/io/legado/app/data/dao/`
- 表名/列名对照 `src/main/resources/schema.sql`
- 查询 `= withLock { db.queryOne/queryList/queryValue(sql, args, Class) }`；写操作语句体
- `IN (:list)` 参数传 `listOf(list)`；多参数按 SQL 中 `:name` 出现顺序传位置参数

## 6. 提交与测试纪律

- 每 Task：`compileKotlin` 0 错误 + 最小验证 → 更新 STATUS.json → `git add -A && git commit`（身份已配置）→ `git push`
- 每 Part：`tools/test_backend.sh` 全过 → STATUS.json 置 part done
- 不要 `pkill` 乱杀；测试脚本已内置精确 PID 清理

## 7. Part 1 数据层会话经验（2026-08-10，交接给下一会话）

> 本会话从「读 docs → 实现剩余 21 DAO → Part 1 联测 → 推送」全程完成。
> 以下只写**新踩的坑**和**已验证有效的方法**，既有规则（第 1/2/3/6 节）不重复。

### 7.1 本轮完成
- 24/24 DAO（21 个新 Impl，SQL 对照 legado Room @Query）
- `--dao-smoke-test` 入口（Main → DaoSmokeTest，25 项断言）集成进 `tools/test_backend.sh`
- 提交：`56e2b12`（Part 1 数据层）、`baf4fb5`（README 状态）—— 均已在 main

### 7.2 本轮新踩的坑（别重踩）

1. **SqlExecutor.bind() 字面 `?` 不绑定（最致命，静默错误）**
   原实现只把 `:name` 转 `?` 并收集 bindArgs；SQL 里手写的 `?` 不被处理 → **参数永不绑定，查询静默返回空/写入不生效，且不报错**。T1.1 集成测试用 python 直写 SQL 掩盖了此问题，Kotlin DAO 实际从没跑通过。已修：bind() 对字面 `?` 也消耗位置参数（与 `:name` 共用 argIdx 顺序计数器）。**教训：DAO 写完必须跑真实 CRUD 冒烟断言返回值，不能只看"不抛异常"。**

2. **queryList(..., String::class.java) 反射实例化失败**
   `allGroupsUnProcessed`/`getGroupNames`/`localBookFileNames`/`findExistingSourceUrls` 等标量列表查询，对 String 反射找无参构造 → 抛 IllegalStateException。已修：toList() 遇基础类型直接取第一列标量（convertScalar）。

3. **`collate localized`（Android 专属 collation）**
   bookmarks/readRecord/highlights 的排序 SQL 用它，桌面 SQLite 无此 collation 直接报错。已用 `org.sqlite.Collation.create(conn, "localized", ...)`（大小写不敏感比较）在 SqliteDatabase.init 注册，SQL 保持原样不动。**不要**去改 SQL 删 collate。

4. **`.gitignore` 的 `data/` 误忽略整个源码目录（严重）**
   gitignore 的 `data/` pattern 匹配**任意层级** data 目录 → `backend/src/main/kotlin/io/legado/desktop/data/` 整个数据层（含此前"已完成"的 3 个 DAO）**从未被提交**，remote 上只有骨架。已修为 `/data/`（锚定仓库根）。**教训：commit 前 `git status --short | wc -l` 核对新增源码真的进库；gitignore 目录规则一律 `/xxx/` 锚定。**

5. **sqlite-jdbc 3.50 标量返回类型随值大小变**
   小值返回 Integer、大值返回 Long，`as? Int`/`as? Long` 严格转换会漏（`queryValue(..., Long::class.java)` 拿到 Integer 返回 null）。已修：queryValue 做 Number 归一化（convertScalar）。**新写标量查询放心用 queryValue，别再手写 as?。**

6. **Flow<List<T>>.toList() 是 List<List<T>>**
   DAO flow 方法（`flowAll()` 等）返回 `Flow<List<X>>`，`.toList()` 后是外层集合，断言时要 `.flatten()` 或 `.first()` 拿内层。

7. **Kotlin 字符串跨行拼接会坑自动化比对**
   `"..." + \n "..."` 拼接的 SQL，正则/脚本提取时被截断（误报差异或漏提取）。比对前先还原拼接（`re.sub(r'"\s*\+\s*$', '', t, re.M)` + 去行首 `"`），再提内容。

8. **实体必填参数 → 反射无参构造**
   Room 用构造器映射，桌面 SqlExecutor 反射要无参构造。ReadRecordBook/ReadRecordShow/RssReadRecord(record)/KeyboardAssist(key,value) 已补默认值。**后续迁移实体：无默认值的必填参数都补默认值。**

### 7.3 已验证有效的方法（照用）

- **DAO 冒烟模式**：`--dao-smoke-test`（Main 里 init 后跑 `DaoSmokeTest.run()` 返回失败数，`exitProcess(0/1)` 跑完即退，无需 kill）；test_backend.sh 第 4.5 节调用它并 `grep -c '✅'` 计数。冒烟要覆盖：每个 DAO insert/query/update/delete + flow 收集 + `IN(:list)` + collate localized + 外键路径。
- **忠于原版核对三件套**（写完后必跑）：
  1. 接口方法签名 diff：`diff <(grep -oP '(fun|val|suspend fun) \w+' impl | sort) <(...原版...)`
  2. INSERT/UPDATE 列清单 vs schema.sql 自动化对照（还原拼接后提取，脚本见会话记录）
  3. 递归 CTE 组过滤（ReplaceRule/RssSource/SearchBook）逐字对比
- **测试数据唯一性**：books 有 UNIQUE(name, author)；searchBooks.origin 外键依赖 book_sources（先插源后插搜索书）；chapters.bookUrl 级联依赖 books（先插书）。冒烟里用 `System.currentTimeMillis()` 拼唯一 key。
- **`queryValue` 选型**：Int/Long/Boolean/String 全由 convertScalar 归一化，放心传目标类型。

### 7.4 当前状态（2026-08-10 会话结束时）
- Part 0 ✅ / Part 1 ✅（24/24 DAO，冒烟全过），STATUS.json 已同步
- remote main 最新：`baf4fb5`
- 下一步：**Part 2 配置与网络层**（T2.1 配置系统 → T2.2 OkHttp/StrResponse/SSL/解压 → T2.3 Cookie → T2.4 代理），详见 PLAN.md 第 3 节

---

## 8. Part 2 配置与网络层会话经验（2026-08-10，交接给下一会话）

> 本会话从「读 docs → 核对已迁移的 Part 2 代码 → 编写 --net-smoke-test 16 项断言 → 修正 ReadTipConfig → 联测 → 推送」全程完成。
> 结论：**Part 2 代码在 Part 0/1 时已迁移完毕且编译通过，本轮工作 = 验收测试 + 忠于原版核对**。

### 8.1 本轮完成
- 新增 `--net-smoke-test` 入口（Main → NetSmokeTest，16 项断言，跑完即退出）：
  - **T2.1 配置**：AppConfig/LocalConfig/SourceConfig 读写 + config.json 落盘重解析（重启保持）
  - **T2.2 HTTP**：本地 HttpServer（JDK com.sun.net.httpserver）验证 StrResponse / gzip / deflate / brotli 解压 / UA 注入 + example.com 真实 https+br
  - **T2.3 Cookie**：Set-Cookie → session(内存) + persistent(cookies 表) + loadRequest 请求头合并回显
  - **T2.4 代理**：parseProxyConfig 解析 + HTTP 代理真实转发链路 + SOCKS5 RFC1929 握手帧 mock
- 修复 **ReadTipConfig.kt 硬编码资源数组不忠于原版**（见 8.2-4）
- `tools/test_backend.sh` 集成 4.7 段（node 预生成 br 字节 → --net-smoke-test）

### 8.2 忠于原版核对（本轮重点，diff 全部通过）
| 文件 | 结论 |
|---|---|
| OkHttpUtils / DecompressInterceptor / HttpLogInterceptor / HttpLogRecord / HttpLogSanitizer / HttpProxyConfig / Socks5Proxy / RequestMethod / OkHttpExceptionInterceptor / OkhttpUncaughtExceptionHandler | 与原版**逐字一致**（0 差异） |
| StrResponse | 仅差 `@Keep`（Android 注解，已裁） |
| SSLHelper | 仅 `@SuppressLint`→`@Suppress`（等价） |
| HttpHelper | 仅裁剪 Cronet/Glide ProgressResponseBody/ReadManga.rateLimiter（明确不移植项） |
| CookieManager / CookieStore | 仅裁剪 android.webkit 同步（applyToWebView/setWebCookie 留注释）；`TextUtils.isEmpty`→`isNullOrEmpty`（语义等价） |
| AppConfig | 仅 `appCtx.getPrefXxx`→`getPrefXxx` + 移除 OnSharedPreferenceChangeListener/CanvasRecorder（Android 专属）；行为逐项比对无差异 |
| LocalConfig / SourceConfig | SharedPreferences("local"/"SourceConfig") → DesktopEnv + `local_`/`SourceConfig_` 前缀（隔离语义一致） |
| ReadBookConfig | 仅 filesDir→DesktopEnv.homeDir + Drawable/Bitmap 裁剪；bgMeanColor/textColor 等字段原版就有 |

### 8.3 本轮新踩的坑

1. **ReadTipConfig 硬编码资源数组必须对照原版 values-zh/arrays.xml（严重，已修）**
   原版 `tipNames` 来自 `R.array.read_tip` = **无/书名/标题/时间/电量/电量%/页数/进度(%)**，桌面版误写为"默认/间隔/上下/左右"；`tipColorNames`（跟随内容/自定义）与 `tipDividerColorNames`（默认/跟随内容/自定义）也错写为颜色名。**教训：凡原版读 `R.array.xxx`/`getString` 的地方，桌面版硬编码必须照抄 `values-zh/arrays.xml`（或 values/strings.xml）的对应值，不能凭印象编。**

2. **SOCKS5 mock 服务器解析 auth 帧（RFC1929）**
   认证帧 `[0x01, ulen, user..., plen, pass...]` —— `auth-hdr` 第二字节就是 ulen，**不要**再 `input.read()` 一次（会读到 'u'=117 当 ulen 导致读爆超时）。原版 `Socks5Protocol.connect` 发送的帧完全正确，错在 mock。

3. **断言必须命中对端点（测试自坑）**
   Cookie 注入验证原写请求 `/ua`（只回显 User-Agent 头）→ 永远断言失败误判 loadRequest 失效；实际是端点选错。改为 `/cookie` 端点回显请求的 Cookie 头。

4. **brotli 测试字节生成**：环境无 python brotli 库，但 **node 内置 `zlib.brotliCompressSync`**；test_backend.sh 用 node 预生成 `/tmp/legado-net-test/hello.txt.br`，Kotlin 从 `System.getProperty("java.io.tmpdir")` 读取（文件缺失则本地 br 断言跳过，example.com 真实 br 兜底）。

### 8.4 已验证有效的方法（照用）

- **本地起 HTTP 服务器测网络层**：JDK 内置 `com.sun.net.httpserver.HttpServer`（零依赖）。gzip 用 `GZIPOutputStream`；deflate 用 `DeflaterOutputStream(Deflater(level, true))`（nowrap，匹配原版 `Inflater(true)`）；br 用预生成字节。
- **HTTP 代理模拟**：OkHttp 走 HTTP 代理时向代理发**绝对 URI 形式请求行**；HttpServer 收到后返回 `PROXY-OK:${requestURI}` 前缀即可证明请求真的经代理转发（对照：直连返回 TARGET-OK）。
- **SOCKS5 协议验证**：mock ServerSocket 按 RFC1929 逐帧断言（greeting 05 01 02 → 选 05 02 → auth [01,ulen]+user+[plen]+pass → reply [01,00] → connect [05,01,00,03]→reply），服务器线程异常用 `AtomicReference<Throwable>` 回传主线程（否则线程内 require 失败会静默超时 15s）。
- **`getProxyClient(proxy)` 缓存**：key 是 `ProxyConfig`（protocol/host/port/credentials），同一 proxy 串复用 client；socks5 带凭据走 `Socks5SocketFactory` + `socks5ProxyDns`（合成地址 0.0.0.0），HTTP(S) 走标准 `Proxy` + 可选 `Proxy-Authorization`。

### 8.5 当前状态（2026-08-10 会话结束时）
- Part 0 ✅ / Part 1 ✅ / **Part 2 ✅**（--net-smoke-test 16 项断言 + test_backend.sh 全绿）
- STATUS.json 已同步（lessons 追加 11/12/13）
- remote main 最新：本会话提交后更新
- 下一步：**Part 3 规则引擎**（T3.1 AnalyzeRule 基础 → T3.2 AnalyzeUrl → T3.3 Rhino 集成 → T3.4 联测），详见 PLAN.md 第 4 节

## 9. Part 3 规则引擎会话经验（2026-08-10，交接给下一会话）

> 本会话从「读 docs → 忠于原版核对 → 补齐资源 → 编写 --rule-smoke-test 23 项断言 → 联测 → 推送」全程完成。
> 结论：**Part 3 代码在 Part 0 时已迁移完毕且编译通过，本轮工作 = 资源补齐 + 验收测试 + 忠于原版核对**（与 Part 2 同模式）。

### 9.1 本轮完成
- 新增 `--rule-smoke-test` 入口（Main → RuleSmokeTest，23 项断言，跑完即退出）：
  - **T3.1 AnalyzeRule 基础**（9 项）：CSS 文本/属性/多元素列表、XPath 文本/属性、JSONPath 列表/自动识别（`$.` 开头自动 Mode.Json）、复合规则 `@css:...@text@js:...`、变量 `put`+`@get:{var}`
  - **T3.2 AnalyzeUrl**（5 项）：`{{key}}`/`{{page}}`/`{{js}}` 内嵌、`@js:` 整段重写（result 变量替换）、`,{options}` POST JSON body、headerMap UA 注入（本地 HttpServer 回显真实链路）
  - **T3.3 Rhino 集成**（7 项）：RhinoScriptEngine 算术/函数、AnalyzeRule.evalJS java 绑定（`java.base64Encode('abc')`）、SharedJsScope CryptoJS.MD5（补资源后）、JsSourceEngine mainJs 顶层函数/NativeObject JSON 归一化/callFunctionIfExists
  - **T3.4 联测**（2 项）：规则源（搜索 HTML→解析书名）+ JS 源（getSearchBooks + java.ajax→JSON）各跑通一次
- 补齐资源：`scripts/cryptojs.min.js` + LICENSE（原版 assets/scripts/）+ `js_source_template.js`（Part 4 备用）
- `tools/test_backend.sh` 集成 4.8 段
- 提交：见 git log（Part 3 规则引擎）

### 9.2 忠于原版核对（本轮重点，diff 全部通过）
| 范围 | 结论 |
|---|---|
| analyzeRule 12 文件（AnalyzeByJSoup/XPath/JSonPath/Regex/RuleAnalyzer/RuleData/…） | **0 差异** |
| AnalyzeRule.kt | 仅 30 行 diff：WebView webJs 抛 NoStackTraceException、TextUtils→isNullOrEmpty、@Keep 裁剪（等价） |
| AnalyzeUrl.kt | 仅 66 行 diff：useWebView→false 分支、BackstageWebView→okhttp、Cronet→null、GlideUrl/MediaItem 裁剪、Base64 等价（合理裁剪） |
| jsSource 7 文件 | 仅 LruCache→SimpleLruCache、`getString(R.string...)`→硬编码中文（等价） |
| com/script 全部 18 文件（Rhino 引擎，原版 modules/rhino） | 几乎 0 差异；RhinoClassShutter 仅删 android.Context + SDK_INT→true；ClassNameMatcher 仅 LruCache→ConcurrentHashMap |
| help/JsExtensions.kt | 213 行 diff 全部是 WebView/视频裁剪为抛错 + LruCache/路径等价替换（符合约束） |
| help/JsEncodeUtils / utils/JsURL / JsonExtensions / JsoupExtensions | Base64→java.util.Base64（NO_WRAP 等价）、@Keep 裁剪、0 差异 |
| model/SharedJsScope.kt | LruCache→SimpleLruCache、ACache→SimpleACache、assets→classpath 资源（等价） |
| utils/EncoderUtils.kt | 64 行 diff 是 **Android Base64 flags 语义兼容实现**（DEFAULT/NO_PADDING/NO_WRAP/CRLF/URL_SAFE + 无 padding 容错），JS 书源会传 flags，必须保持 |

### 9.3 本轮新踩的坑（别重踩）

1. **cryptojs.min.js 资源缺失 = 静默失效（最隐蔽）**
   `SharedJsScope.loadCryptoJs()` 用 `runCatching { getResourceAsStream(...) }` 包着，资源不存在只记 Debug 日志返回 null → `getCryptoScope` 返回 null → JS 源里所有 crypto 绑定（CryptoJS）静默不可用，**不报错**。核对方法：`grep -rn "getResourceAsStream" backend/src/main/kotlin | grep scripts` 后对照 resources/ 目录。已补 `scripts/cryptojs.min.js`（64234 字节，原版 assets 直拷）。

2. **StrResponse.body 是 String?**：`au.getStrResponse().body.contains(...)` 编译错（nullable receiver），测试里要 `body ?: ""`。DAO 冒烟时 queryValue 已归一化，网络层没有。

3. **const val 不能调函数**：`private const val HTML = """...""".trimIndent()` 编译错（Const initializer），改 `private val`。

4. **workspace_shell 并行时序坑**：同一消息里两个工具调用并行执行时，若一个在改文件一个在编译，编译可能读到旧文件报"幽灵错误"。**先确认文件落盘（grep 核对）再单独跑编译**。

### 9.4 已验证有效的方法（照用）

- **规则引擎冒烟模板**：`AnalyzeRule(ruleData = RuleData(), source = BookSource(...))` + `setContent(HTML/JSON)` → `getString/getStringList`。JSON 内容 `setContent` 自动 isJSON；`$.` 开头规则自动 Mode.Json；XPath `//` 开头自动识别；`@css:选择器@text/@href`（@ 后操作符：text/ownText/html/all/任意属性名）。
- **AnalyzeUrl 冒烟**：构造 `AnalyzeUrl(url, key=, page=, baseUrl="", source=)` → `getStrResponse()`（内部 runBlocking，可同步调）。`{{key}}`/`{{page}}` 是 JS 绑定替换；`@js:代码` 整段重写 URL（js 内用 `result` 引用原 URL）；`,{...}` options 里 method/body（JSON 字符串）在 ruleUrl 整体上先做 `{{}}` 替换。
- **JS 源冒烟**：`BookSource(bookSourceUrl=, mainJs="function ...")` + `JsSourceEngine(source).callFunction("fn", listOf("argName" to value))`。args 绑定进 scope 既是参数也是环境变量（key/page 惯例）。
- **CryptoJS 验证**：`SharedJsScope.getCryptoScope(this, null)` → `RhinoScriptEngine.eval("CryptoJS.MD5('abc').toString()", scope)` == `900150983cd24fb0d6963f7d28e17f72`。
- **本地 mock**：JDK `com.sun.net.httpserver.HttpServer` 起两个（T3.2/T3.4 各一组，端口 0 随机），回显 PATH/query/UA/body，断言命中回显串。

### 9.5 当前状态（2026-08-10 会话结束时）
- Part 0/1/2/3 ✅（--rule-smoke-test 23 项断言 + test_backend.sh 全绿），STATUS.json 已同步（lessons 追加 14）
- remote main 最新：本会话提交后更新
- 下一步：**Part 4 书源与读书引擎**（T4.1 SourceHelp 书源管理 → T4.2 jsSource → T4.3 WebBook → T4.4 联测），详见 PLAN.md 第 5 节
