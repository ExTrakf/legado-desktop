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
- `help/storage/Backup*`、`ImportOldData`（T6.3）
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

## 10. Part 4 书源与读书引擎会话经验（2026-08-10，交接给下一会话）

> 本会话从「读 docs → 忠于原版核对 → 实现 CheckSourceRunner（T4.1 唯一缺口）→ 编写 --source-smoke-test 18 项断言 → 修 SqlExecutor JSON 往返 → 联测 → 推送」全程完成。
> 结论：**Part 4 代码在 Part 0 时已迁移完毕，唯一裁剪缺口是 CheckSource 校验执行（原 CheckSourceService）**；本轮工作 = 等价迁移 + 验收测试 + 发现并修复 Part 1 遗留 DAO bug。

### 10.1 本轮完成
- **CheckSourceRunner.kt**（新建）：原 `CheckSourceService`（Android Service）等价迁移 → 纯 JVM 协程 Runner。
  - `check/checkSource/doCheckSource/checkBook/isDomainReachable/parseCheckSourceEndpoint/selectCheckSourceChapter` 业务逻辑**逐字保留**（脚本比对确认，仅 `getString(R.string.chapter_list_empty)`→"目录列表为空" 已对照 values-zh）
  - 裁剪：BaseService/Notification/Intent/生命周期（stopSelf 等）；通知 → 日志 + postEvent 保留调用点
  - "已有书源在校验"原版 toast+return（**不抛异常**），桌面版 LogUtils+return 等价
  - `CheckSource.start/stop/resume` 恢复桌面实现（start 直接驱动 Runner，返回 `"desktop:check:$sessionId"`）
- 新增 `--source-smoke-test` 入口（Main → SourceSmokeTest，18 项断言，跑完即退出）：
  - **T4.1**（5 项）：SourceHelp 导入/启停/删除、JsSourceUpsert 保存 JS 单文件源、CheckSource 全项校验（本地 mock 规则源）→ PASSED
  - **T4.2**（4 项）：JsSourceBook 搜索/详情/目录/正文
  - **T4.3**（6 项）：WebBook 规则源 搜索→详情→目录→正文 全链路 + ContentProcessor 替换净化 + SearchBookShelfHelp 加书架
  - **T4.4**（3 项）：多源搜索结果入库往返 + SearchModel 双源合并去重（origins 聚合）+ BookDao.upProgress 进度存取
- **修复 SqlExecutor 复杂类型 JSON 往返**（Part 1 遗留，见 10.2-1）
- `tools/test_backend.sh` 集成 4.9 段（--source-smoke-test，18 项）

### 10.2 本轮新踩的坑（别重踩）

1. **BookSource.rule\* 字段 JSON 往返（最隐蔽，Part 1 遗留）**
   Room 原版用 `@TypeConverters` 把 ruleSearch/ruleToc/ruleContent/ruleBookInfo/ruleExplore/ruleReview 存为 **JSON 字符串列**。桌面版 SqlExecutor 旧实现：bind 时对复杂对象 `toString()`（存成 `SearchRule(checkKeyWord=null, ...)` 垃圾）、查询时反射无法还原（静默 null）。**后果**：`insertBookSource` 后从库 `getBookSource` 拿到的源 rule\* 全 null → 搜索/校验静默返回空（T4.1 校验"搜索失效"、T4.4 搜索空）。修复：`bindAll` 对非标量（String 外）`GSON.toJson(v)`；`convert` 复杂类型 `runCatching { GSON.fromJson(str, type) }.getOrNull()`（utils.GSON 已注册全部规则类 jsonDeserializer）。**教训：DAO 冒烟必须覆盖复杂字段往返，不能只看简单 CRUD。**

2. **JsSourceUpsert.save 只接受 JS 单文件源格式，不是标准 JSON**
   原版 web 书源（JS 源）导入格式 = `var config = {...}` + 顶层函数（search/getChapters/getContent），JsSourceConfig.extract 把文本**当 JS 执行**后取 config 对象。传 GSON.toJson(BookSource) 会报 "JS源脚本执行失败: missing ; before statement"。规则源导入走 `SourceHelp.insertBookSource(BookSource)`（实体直插），JS 源导入走 `JsSourceUpsert.save(jsText)`。

3. **JS 源相对 URL 必须用 baseUrl 绑定拼绝对（测试数据坑）**
   `java.ajax("/jssearch?key=x")` 相对路径在 JsExtensions.ajax → AnalyzeUrl(baseUrl="") 下无法请求，ajax 失败返回**异常堆栈字符串**（`runCatching.getOrElse { it.stackTraceStr }`），`JSON.parse(堆栈)` 报 `Unexpected token: j`（j=java.lang 的 j）——极易误判为 Rhino 语法问题。原版模板写法：`java.ajax(config.bookSourceUrl + "/search?...")`；JS 单文件源里用绑定 `baseUrl`（=source.getKey()）拼。**教训：JS 源测试数据一律绝对 URL；报 Unexpected token: j 先查 ajax 返回值。**

4. **WebBook 各 Await 的 URL 拼接 baseUrl 不同**
   `getBookInfoAwait` 用 baseUrl=bookSource.bookSourceUrl（相对 bookUrl 可拼）；`getChapterListAwait` 用 AnalyzeUrl(baseUrl=book.bookUrl)（bookUrl 必须是绝对，否则 tocUrl 相对拼不出）；正文 AnalyzeUrl(baseUrl=book.tocUrl)。**测试构造 Book 时 bookUrl/tocUrl 全部用绝对 URL**（真实流程里来自搜索结果=绝对）。

5. **SearchModel 结果 insert 到 searchBooks 表**：搜索流程里 `appDb.searchBookDao.insert`（NOT NULL bookUrl），SearchBook 的 bookUrl 为空即报 SQLITE_CONSTRAINT_NOTNULL 且被 flow catch 吞掉（只 AppLog）→ 表现为"搜索结果为空"。先定位 insert 再查搜索。

6. **Rhino 验证小工具**：htmlunit fork 包名是 `org.htmlunit.corejs.javascript`（非 org.mozilla）；API 变了（`initStandardObjects()` 返回 TopLevel、`evaluateString(VarScope,...)`、ScopeObject implements VarScope）。写 Java 复现时 cast `(VarScope) scope`。

### 10.3 已验证有效的方法（照用）

- **忠于原版核对 CheckSourceService**：python 大括号配对提取函数体 + 归一化（去注释/空白/包名）diff，`checkSource/isDomainReachable/doCheckSource/parseCheckSourceEndpoint` 逐字一致，`checkBook` 仅字符串等价。比人眼 diff 可靠。
- **CheckSource 校验测试**：`Debug.tryStartCheckSession()` → `CheckSource.start(Any(), parts, sessionId)` → 轮询 `!Debug.isChecking(sessionId) && CheckSourceRunner.activeSessionId == null` → `Debug.getCheckSnapshot(sessionId, urls)` 断言 `status == CheckSourceStatus.PASSED`。checkDomain=false 走本地 mock；校验会真实改源（addGroup/respondTime 写回）。
- **本地双 HttpServer mock 两个书源**（规则源 + JS 源，端口 0 随机），同 RuleSmokeTest 模式。
- **JS 单文件源模板**：`var config = {bookSourceUrl, bookSourceName, bookSourceType, bookSourceGroup, lastUpdateTime}` + `search/getBookInfo/getChapters/getContent`；URL 全用 `baseUrl + "/..."`。
- **正文替换验证**：替换在**阅读层**（ContentProcessor.getContent），不在 WebBook.getContentAwait（只取原始正文）。直接 `ContentProcessor.get(book).getContent(book, chapter, raw, includeTitle=false)` 断言。ReplaceRule 用 `scope` 字段（LIKE 书名/源）匹配，`isEnabled/isRegex/scopeContent`。

### 10.4 当前状态（2026-08-10 会话结束时）
- Part 0/1/2/3/**4** ✅（--source-smoke-test 18 项断言 + test_backend.sh 9 PASS 全绿），STATUS.json 已同步（lessons 追加 15）
- remote main 最新：本会话提交后更新
- 下一步：**Part 5 API 层**（T5.1 HttpServer 路由整合 → T5.2 书源/RSS/替换规则 API → T5.3 书籍 API → T5.4 WebSocket → T5.5 端到端联测），详见 PLAN.md 第 6 节

## 11. Part 5 API 层会话经验（2026-08-11，交接给下一会话）

> 本会话从「读 docs → 迁移 web/HttpServer+WebSocketServer+socket/ 与 BookController/HttpLogController → 编写 --api-smoke-test 29 项断言 → 联测 → 推送」全程完成。
> 结论：**Part 5 增量迁移 8 个文件（web/ 包 5 + api/controller 2 + ApiSmokeTest），全部编译通过；HttpApiServer 被原版 HttpServer 取代**。

### 11.1 本轮完成
- **T5.1 `web/HttpServer.kt`**（从原版迁移）：全路由（书源/RSS/替换/书籍/HTTP 日志/阅读配置/JS 源保存）+ OPTIONS CORS（带 Origin 回显）+ `x-legado-token` 令牌保护（写路由 + HTTP 日志读路由）+ 404 JSON + `/api/health`。裁剪：Review 路由、AssetsWeb 静态资源、WebService、Bitmap 分支（保留 ByteArray 分支备 T6.2）。
- **T5.2**：HttpLogController 迁移（HttpLogStore 已在桌面版存在）；书源/RSS/替换 controller 是 Part 0 已迁，仅接路由。
- **T5.3 `api/controller/BookController.kt`**：书架/加删书/目录 refreshToc/正文+ContentProcessor/进度/阅读配置；cover·image·addLocalBook 占位返回 "T6.x 实现"；AppWebDav/ReadBook 裁剪。
- **T5.4 `web/WebSocketServer.kt` + `web/socket/`**：searchBook/bookSourceDebug/rssSourceDebug 三 socket，端口 = HTTP+1；令牌协议校验原版一致；`runOnIO{}` → `launch(IO){}` 等价替换。
- **Main.kt**：HttpApiServer 删除，改 HttpServer + WebSocketServer(port+1)；新增 `--api-smoke-test`（进程内起双服务→自测→退出）。
- **T5.5 `ApiSmokeTest.kt`**：29 项断言（HTTP 全路由 + raw socket WS 客户端 + 端到端闭环），test_backend.sh 集成 4.10 段。
- 提交：见 git log（Part 5 API 层）

### 11.2 本轮新踩的坑（别重踩）

1. **原版 ReplaceRuleController.saveRule/delete 从不 setData（最易误判为 bug）**
   成功路径返回的 ReturnData 恒 `isSuccess:false`、`errorMsg="未知错误,请联系开发者!"`（ReturnData 默认值）——这是**原版行为**（web UI 只看 getReplaceRules 列表核对），不是迁移丢失。**忠于原版不改**。测试/前端必须以列表接口核对生效状态，不能断言 isSuccess。教训16。

2. **ReplaceRuleDao.delete 按主键 id 删除**
   `DELETE FROM replace_rules WHERE id = ?`。API 客户端若只传 name/pattern（id 默认 0）→ 静默删不掉。删除前必须从库里取完整规则（含 id）回传。教训17。

3. **raw socket WS 客户端必须消费 101 响应头（最隐蔽）**
   握手响应 `HTTP/1.1 101...` 后还有 `Upgrade/Connection/Sec-WebSocket-Accept` 头到空行为止。只读状态行就返回 → 剩余头字节被当帧解析：searchBook 表现为 `Read timed out`（第一个帧是垃圾、后续无数据）、debug 表现为"收不到日志但收到假 close 帧"。教训18。

4. **OPTIONS 预检的 Allow-Origin 只在带 Origin 头时回显**
   原版 `addWebHeaders`：`origin?.let { addHeader("Access-Control-Allow-Origin", it) }`。无 Origin 的 curl/HttpClient 预检没有该头属正常。测试要模拟浏览器带 `Origin: http://localhost:5173`。JDK HttpClient 发 OPTIONS 用 `.method("OPTIONS", BodyPublishers.noBody())`（默认会变 POST）。教训19。

5. **冒烟必须用全新 LEGADO_DESKTOP_HOME**
   `saveReplaceRule` 即使 isSuccess=false 也已真实落库；上一轮残留的 `萧炎→XY` 替换规则会让下一轮 `getBookContent` 断言"萧炎"失败（ContentProcessor 已替换成 XY）。smoke 前必须 `rm -rf` 数据目录。教训20。

6. **--api-smoke-test 端口与 test_backend.sh 主服务冲突**
   Main 的 api-smoke 分支用 `--port` 绑服务器（默认 2323），与脚本已启动的主服务端口冲突 → 集成脚本传 `--port 2433`（WS 2434）避开。教训21。

### 11.3 已验证有效的方法（照用）
- **raw socket WS 客户端模板**（ApiSmokeTest.TestWsClient）：手写握手（Sec-WebSocket-Key=base64(16B)）+ 消费响应头到空行 + 掩码文本帧（0x81 + 0x80|len + 4B mask + 异或）+ 解帧（0x1 文本 / 0x8 close / 0x9 ping→回 pong）。服务端帧不掩码。
- **WS 令牌**：`BookSourceController.jsSourceWebSocketProtocol(token)`（internal，同模块可直接调）生成 `legado.token.<base64url>`；握手头 `Sec-WebSocket-Protocol: legado, <protocol>`。
- **端到端 mock**：单 HttpServer 起 `/search /book/1 /toc/1 /content/1 /rss.xml`；规则源 searchUrl 相对 baseUrl；WS searchBook 直接走 SearchModel（allEnabledPart），搜索完自动 close（Search finish）。
- **NanoWSD close() 从任意线程安全**：state==OPEN 时 close() 会同步 sendFrame(close)，客户端能收到 close 帧（搜索完成、调试 state 1000 都会正常关闭）。
- **忠于原版核对**：HttpServer/WebSocketServer/三个 socket 与 `E:\repos\legado\app\src\main\java\io\legado\app\web\` 逐字 diff，仅差 WebService/Review/AssetsWeb/Bitmap/`appCtx.getString`→硬编码中文（对照 values-zh）、`runOnIO`→`launch(IO)`。

### 11.4 当前状态（2026-08-11 会话结束时）
- Part 0/1/2/3/4/**5** ✅（--api-smoke-test 29 项断言 + dao/net/rule/source 全 PASS），STATUS.json 已同步（lessons 追加 16~21）
- remote main 最新：本会话提交后更新
- 下一步：**Part 6 本地书籍/封面/图片/备份**（T6.1 本地书籍解析 → T6.2 封面图片 → T6.3 备份导入，后两项可选），详见 PLAN.md 第 7 节；BookController 中 cover/image/addLocalBook 占位与 `readContent` 本地分支待 T6.x 补全

## 12. Part 6 本地书籍/封面图片/备份会话经验（2026-08-11，交接给下一会话）

> 本会话从「P5 忠于原版核对 → 删去 MCP → 迁移 vendored 库 + localBook + 封面图片 + 备份导入 → --local-smoke-test → 联测 → 推送」全程完成。
> 结论：**Part 6 增量迁移约 150 文件（vendored me.ag2s.* + lib.mobi + localBook + storage + image），PdfFile 按计划 stub；MCP 明确不移植已全量删除**。

### 12.1 本轮完成
- **P5 忠于原版核对**（上一会话产物）：WebSocketServer/socket 与 BookController/HttpLogController/ReturnData/ReaderProviderRoutes 逐字等价；**发现并修复一处真实分歧**——桌面 SearchModel 迁移时丢了 `CallBack.getSearchScope()`（原版按 AppConfig.searchScope 限制搜索范围），已迁移 SearchScope（MutableLiveData → SimpleLiveData stub）+ 接回 SearchModel + WS + SourceSmokeTest 回调；HttpLogController 补回 setRecording（原版仅 MCP 调用）。
- **删去 MCP**：PLAN.md / README.md / API.md / ARCHITECTURE.md / HANDOVER.md / STATUS.json 全部移除（架构图、依赖图、任务表、T6.3、T6.4→T6.3 重编号）。
- **T6.1 本地书籍**：vendored `me.ag2s.epublib`(76)+`me.ag2s.umdlib`(9)+`io.legado.desktop.lib.mobi`(34)；localBook 全量迁移（TextFile/EpubFile/MobiFile/UmdFile/BaseLocalBookParse/CloseableCache/LocalBook）；`BookController.addLocalBook`/`refreshToc` 本地分支/`BookHelp.readContent` 本地正文接入；`Book.isLocalModified()`/`getLocalUri` 缓存+书库回退忠实实现。PdfFile stub（Android PdfRenderer 无 JVM 等价）。
- **T6.2 封面图片**：ImageUtils 解密接入 BookHelp.saveImage；ImageProvider 字节化；BookCover 纯逻辑；BitmapUtils ImageIO 等价；coverRule.json 资源补齐。
- **T6.3 备份导入**：Backup 导出 + Restore 导入（config.xml SharedPreferences XML 兼容 + servers AES + fixture）；AppWebDav/视频/主题/调度裁剪。
- `--local-smoke-test`（11 项断言）+ test_backend.sh 4.11 段。

### 12.2 本轮新踩的坑（别重踩）
1. **TXT 目录规则选择启发式有"死区"**：`getTocRule` 中 `contentLength` 在 100~1000 字符时既不 csNum++ 也不 numE++（被跳过）；短段落（<100 字符）计入 numE 导致规则全拒。**测试 TXT 每章正文必须 >1000 字符**，否则选不出规则（tocUrl 空 → 无规则分章）。教训22。
2. **文件开头"第一章"被 lookbehind 规则跳过**：默认第一条规则 `(?<=[　\s])...` 要求前导空白，文件开头的"第一章"不匹配 → 被当作"前言"章。这是**原版忠实行为**，测试断言要允许前言章。前加书名/作者行使第一章被匹配。教训23。
3. **PDF 无 JVM 等价**：Android PdfRenderer 渲染 PDF 页为 Bitmap，桌面 JVM 没有；计划明确只做 TXT/EPUB/MOBI/UMD，PdfFile stub 抛 `NoStackTraceException("桌面版暂不支持 PDF 解析")`（本地导入 PDF 报错，其余格式不受影响）。教训24。
4. **vendored 库 Android 依赖面**：epublib 用 android.util.Log/Base64、android.os.Build（BOMInputStream 的 SDK 版本判断→`if(true)`）、androidx.annotation.NonNull、ParcelFileDescriptor（AndroidZipFile）；mobi 用 SparseArray/Pools.SynchronizedPool/PFD；umdlib 仅 NonNull。全部等价替换后**编译零 android import**。AndroidZipFile 用 RandomAccessFile 重写（zip 解析算法逐字保留，含原版 `filepos += len` 语义）；mobi PDBFile 用 FileChannel。教训25。
5. **备份 config.xml 是 Android SharedPreferences XML**：`<map><string name="k">v</string><int>...` 而非 JSON；桌面需 PrefsXml 读写等价。DesktopEnv 需 `allPrefs()`/`putPrefRaw()`（typed 导出/导入）。servers.json 用 hutool AES(Base64)。教训26/27。
6. **SparseArray 的 Kotlin `[]` 访问**：桌面等价类必须 `operator fun get/set`，且 `get` 声明非空（对齐 Android 平台类型语义），否则 mobi 代码 `tagMap[2].tagValues` 编译报错。
7. **Backup 的 `GSON.toJson(list, OutputStream)` 不存在**：用 `GSON.writeToOutputStream(out, list)`（GsonExtensions）。

### 12.3 已验证有效的方法（照用）
- **vendored 库迁移流水线**：`python` 复制 + sed 替换（package 重命名、Log→me.ag2s.base.Log、Base64→java.util.Base64、Build→if(true)、NonNull 剥离）；AndroidZipFile/PfdHelper 手动 RandomAccessFile 重写；SparseArray/SynchronizedPool 桌面等价类。
- **依赖**：`xmlpull:xmlpull:1.1.3.1` + `net.sf.kxml:kxml2:2.3.0`（epublib 的 org.xmlpull.v1）。
- **本地书籍冒烟**：`--local-smoke-test`（TXT 规则选章注意死区/前言；EPUB 用程序生成最小合法 epub——mimetype 首位不压缩 + container.xml + content.opf + toc.ncx + chapter + cover；备份用"导出→清库→恢复→断言一致"round-trip + 手写 Legado fixture zip）。
- **忠于原版核对**：归一化 diff 重叠率 TextFile 0.99 / UmdFile 1.00 / MobiFile 0.91 / EpubFile 0.93（差异均为文档化替换）。

### 12.4 当前状态（2026-08-11 会话结束时）
- Part 0/1/2/3/4/5/**6** ✅（--local-smoke-test 全过 + dao/net/rule/source/api 全 PASS），STATUS.json 已同步（lessons 追加 22~27；MCP 已删除）
- remote main 最新：本会话提交后更新
- 下一步：**Part 7 WebView 兼容 + Compose Multiplatform 前端**（T7.1 引擎层无头 WebView → T7.5 解除调用点裁剪 → T7.6~T7.8 Compose 前端），详见 PLAN.md 第 7.5 节与 docs/WEBVIEW-COMPOSE-PLAN.md

## 13. Part 7 引擎层 WebView 兼容会话经验（2026-08-11，交接给下一会话）

> 本会话从「读原版 WebView 组件 → T7.0 选型调研（发现 KCEF 已废弃）→ 用户拍板 JCEF 直连 → 迁移 T7.1~T7.5 → --webview-smoke-test → 全仓 emoji 清理」全程完成。
> **结论：引擎层纯代码（T7.1~T7.5）已交付并编译通过；JCEF 真实浏览器接入与 offscreen 验证（T7.0）后置，未执行。**

### 13.1 本轮完成
- **T7.0 选型变更（重要，推翻规划原案）**：
  - 规划选定的 **KCEF（DatL4g）已于 2025-10-28 归档**，README 明示 "not recommended / highly outdated"（最后版本 2023.10.11.1）
  - compose-webview-multiplatform 是 Compose UI 组件（需 composable 渲染），且其桌面端底层正是已归档 KCEF
  - **用户拍板：JCEF 直连 + backend 直接引入 + 真实浏览器验证后置到实施阶段**
- **T7.1** `help/webView/WebViewRequestConfig.kt`（diff 逐字 IDENTICAL）+ `PooledWebView.kt`（去 Android upContext）+ `DesktopWebView.kt`（**新增抽象 = JCEF 接缝**：settings/事件回调 onConsoleMessage·onPageFinished·onBeforeBrowse(url,isRedirect)·onResourceLoad/loadUrl·loadHtml/evaluateJavascript(结果回调)/loadJavaScriptUrl/addJavascriptInterface/destroy）
- **T7.2** `WebViewPool.kt`：逻辑逐字（acquire/startCleanupTimer diff IDENTICAL，CACHED=max(threadCount/10,5)、IDLE 5min/LAST 30min、30s 清理协程）；只裁 Android View 操作 + Dispatchers.Main→Default；新增 `DesktopWebViewFactory`（creator 接缝）+ `resetForTest()`（测试接缝）
- **T7.3** `WebJsExtensions.kt`：request funName 分发 + JS_INJECTION/JS_INJECTION2/basicJs + nameXxx/uuid **全部逐字（diff IDENTICAL）**；`RssJsExtensions.kt` 桌面基类（UI 裁剪，analyzeRule 仅绑定 source）；往返通道 = `DesktopWebView.evaluateJavascript("window.$JSBridgeResult(...)")` + CacheManager
- **T7.4** `help/http/BackstageWebView.kt`：getStrResponse(超时+取消)/load/isRule 注入/EvalJsRunnable retry/handleResult/buildStrResponse(redirect priorResponse)/SnifferWebClient 正则拦截 **逐字保留**（section diff 仅 runOnUI→mHandler、WebViewClient 覆写→回调、SSL proceed 由 JCEF 内置、javascript:URL→loadJavaScriptUrl）；Handler→`DesktopHandler`（单线程调度器，post/postDelayed/removeCallbacks/shutdown 语义等价）
- **T7.5** 解除裁剪：AnalyzeRule.getWebJsResult / AnalyzeUrl.executeStrRequest（`if(false)`→`if(this.useWebView && useWebView)`）/ JsExtensions.webView·webViewGetSource·webViewGetOverrideUrl 全部恢复原版
- **`--webview-smoke-test`**：WebViewSmokeTest + Fake DesktopWebView 驱动，**11 项断言 [PASS]**（T7.1 配置 2 / T7.3 桥 4 / T7.4 编排 4 / T7.2 池 1）+ JCEF 真实浏览器段 [SKIP] 后置；test_backend.sh 集成 4.12 段
- **全仓 emoji 清理**：7 个冒烟测试 + test_backend.sh 的 ✅/❌/⚠️ 全部改 ASCII `[PASS]/[FAIL]/[SKIP]`（教训28）

### 13.2 本轮新踩的坑（别重踩）
1. **KCEF 已废弃（T7.0 最致命决策点）**：动手前必须核实库的维护状态。规划文档写于 2026-08-10 选 KCEF，实际 DatL4g/KCEF 2025-10-28 已归档、README 明示不推荐、compose-webview 依赖它且需要 Compose UI 跑起来。**已按用户拍板改 JCEF 直连 + backend 直接引入**。教训29。
2. **runBlocking 单线程事件循环被 busy-wait 阻塞**：`runBlocking { async { suspendCall() } ; while(不挂起){poll} }` 死锁——runBlocking 用当前线程事件循环，`async` 调度的协程要等主协程挂起才会执行，而 `Thread.sleep` 型 busy-wait 不挂起，**协程永不启动**。修复：`CoroutineScope(Dispatchers.Default).async { ... }` 让挂起调用跑独立线程池。教训30。
3. **internal 类型跨包引用必须 import**：`WebViewRequestConfig` 是 internal（help.webView），BackstageWebView（help.http）直接引用类型时要显式 `import ...WebViewRequestConfig`，否则 Unresolved。`toWebViewRequestConfig` 扩展函数同理已 import。教训31。
4. **lambda 赋属性时 `return@属性名` 标签非法**：`onPageFinished = { url -> if (...) return@onPageFinished }` 编译报 "Unresolved label"——属性名不是 lambda 标签。改成 `if (url == BLANK_HTML) { ... }` 结构（语义等价）。教训32。
5. **Windows 控制台 GBK 把 UTF-8 emoji 打乱**：`& exe` 捕获输出 + GBK 控制台 → ✅/❌ 全变 `?`，`grep -c '✅'` 断言计数失效。**规则：代码/脚本/文档一律 ASCII 标记，禁止 emoji**；本仓 7 个冒烟测试已全部迁移 [PASS]/[FAIL]/[SKIP]。教训28。
6. **evaluateJavascript 结果回调桌面是 String?**：Android ValueCallback 给非空 String，桌面接口给 `String?`；BackstageWebView 的 `handleResult(it ?: "null")` 保证空值进 retry 分支（语义等价）。教训33。

### 13.3 已验证有效的方法（照用）
- **忠于原版 diff 三件套**（写完后必跑，本会话已用）：
  1. `WebViewRequestConfig` / `WebJsExtensions.request()` 分发 / companion JS 注入串：strip package+import 后 **逐字 diff IDENTICAL**
  2. BackstageWebView section diff：getStrResponse/handleResult+buildStrResponse/companion+Callback **IDENTICAL**；SnifferWebClient 仅 WebViewClient 覆写签名→回调（正文逐字）
  3. WebViewPool acquire/startCleanupTimer **IDENTICAL**；仅 head 处 Dispatchers.Main→Default
- **Fake DesktopWebView 驱动纯逻辑冒烟**：确定性验证迁移逻辑（不依赖真实浏览器），JCEF 段 [SKIP] 后置——这是"不编造真实浏览器结果"的诚实做法。`DesktopWebViewFactory.creator` 注入 fake/未来 JCEF 实现。
- **Windows 冒烟输出核对**：`cmd /c "...bat --webview-smoke-test > file 2>&1"` 原生重定向 + `[IO.File]::ReadAllText(file, UTF8)` 读回，绕开 PowerShell `&` 的 GBK 解码。断言数用 `[regex]::Matches(raw, '\[PASS\]').Count`。

### 13.4 当前状态（2026-08-11 会话结束时）
- Part 0/1/2/3/4/5/6 done + **Part 7 引擎层 T7.1~T7.5 done**（--webview-smoke-test 11 [PASS] + compileKotlin 0 错误），STATUS.json 已同步（lessons 追加 28~33；notDoing 更新为仅登录 UI 渲染/可见 WebView/webkit Cookie 不移植）
- remote main 最新：本会话提交后更新
- 下一步：**T7.0 JCEF 落地**（backend 引入 JCEF 依赖 → 核实 jcefmaven 坐标 → DesktopWebViewFactory.creator 接 JCEF 实现 → offscreen 最小加载/executeJavaScript/JS 桥真实验证 → 追加 --webview-smoke-test 真实段），详见 STATUS.json blockedBy 与 PLAN.md 第 7.5 节

## 14. Part 7 T7.0 JCEF 接入会话经验（2026-08-11，交接给下一会话）

> 本会话从「选型确认（JCEF 直连）→ 依赖核实 → 探针验证 → 实现 CefEnv/JcefDesktopWebView → 导航竞态修复 → 冒烟真实段 → 连跑验证」全程完成。
> **结论：T7.0 完成，引擎层 T7.0~T7.5 全部就绪；--webview-smoke-test 15 项断言（11 纯逻辑 + 4 真实 JCEF）连跑 3 次全绿。**

### 14.1 本轮完成
- **依赖**：`me.friwi:jcefmaven:146.0.10`（Maven Central；jcef-api + jogl-all/gluegen-rt v2.4.0）；OSR 需要 `--add-exports java.base/java.lang、java.desktop/sun.awt、java.desktop/sun.java2d`（applicationDefaultJvmArgs 已配）；bundle（Chromium natives **~350MB**，非文档估的 200MB）首次运行下载到 `<数据目录>/jcef-bundle`
- **T7.0 探针 `--jcef-probe`**（JcefProbe.kt）：先验证 OSR——**失败**：OSR(JOGL) 无 GL 表面时 load 事件不推进；改 **windowless_rendering_enabled=false + 隐藏 AWT Frame（1x1 无装饰屏幕外）+ createBrowser(url, false, false)**，由 EDT 驱动消息循环，`hello-jcef` 取回成功
- **`help/webView/CefEnv.kt`**：全局单例初始化（CefAppBuilder + bundleDir，幂等）
- **`help/webView/CefWebView.kt`**：`init()` 接线 `DesktopWebViewFactory.creator = { JcefDesktopWebView() }`；`bundleReady()`；bundleDir env 可覆盖；Main 里 `LEGADO_DESKTOP_ENABLE_JCEF=1` 启用
- **`help/webView/JcefDesktopWebView.kt`**：完整实现 DesktopWebView——
  - loadUrl（无 header→loadURL；有 header→CefRequest+loadRequest）、loadHtml（**data: URL**，偏差：相对资源不解析）
  - **evaluateJavascript**：自包含注入 `window.__legadoEval` helper → `eval(js)` → `cefQuery('legado:eval:<id>:<enc>')` → Java pendingEval 回调（结果 JSON 风格，对齐 Android；ERR→"null" 触发 BackstageWebView 重试）
  - onLoadEnd(main frame)→onPageFinished；onLoadingStateChange→isLoading；onBeforeBrowse→CefRequestHandler.onBeforeBrowse；onResourceLoad→**getResourceRequestHandler 返回 CefResourceRequestHandler**（此版本 API 变更）；onCertificateError→callback.Continue()（原 onReceivedSslError→proceed）；console→CefDisplayHandler
  - **JS 桥**：addJavascriptInterface → 页面注入 `window.<name>` Proxy，任意方法经 `cefQuery('legado:invoke:<b64 JSON>')` 反射回 Java；`getFromMemory` 同步语义用页面 `_memData` 对象（JSBridgeResult 协议），evaluateJavascript 命中 JSBridgeResult 模式时前置注入数据
  - **导航竞态**：readyForNav + isLoading 判定，未就绪/加载中 loadURL 挂起、onLoadingStateChange(false) 空闲时应用
  - destroy→browser.close(true)+frame.dispose()+client.dispose()
- **冒烟**：`--webview-smoke-test` 追加 JCEF 真实段（4 项）——真实加载 URL/HTML + eval 取回 innerText、sourceRegex onResourceLoad（img.png）、JS 桥往返；bundle 未下载时 [SKIP] 降级不失败；**连跑 3 次全绿（15/15）**
- 无回归：--dao-smoke-test / --net-smoke-test 仍全绿

### 14.2 本轮新踩的坑（别重踩）
1. **OSR(JOGL) 离屏模式不推进 load（T7.0 最致命）**：jcefmaven 默认 windowless_rendering_enabled=true，但 OSR 需要 JOGL GL 表面；无窗口/无 GLCanvas 时浏览器创建后 load 事件完全不触发（onLoadStart/onLoadEnd 均无）。**修复：windowless_rendering_enabled=false + 隐藏 AWT Frame（1x1 屏幕外）承载 browser.getUIComponent()，EDT 自动驱动消息循环**。教训35。
2. **executeJavaScript/loadURL 必须从 CEF/EDT 线程调用**：从其它线程（协程/调度线程）调用被**静默丢弃**——BackstageWebView 在 DesktopHandler 线程调 evaluateJavascript 时页面 JS 不执行且无错误。修复：SwingUtilities.invokeLater 包装（onCefThread）。教训35。
3. **浏览器加载中调用 loadURL 会被丢弃**：浏览器创建时的初始 about:blank（或池复位 about:blank）加载中调 loadURL 无效果，页面永远停在 about:blank（eval 恒 null → BackstageWebView "js执行超时"）。**修复：readyForNav（首个 onLoadingStateChange(false)/onLoadEnd 后为真）+ isLoading 判定，未就绪/加载中挂起 pendingNav，空闲时应用**。教训36。
4. **`isLoading` 标志单独用会踩初始竞态**：初始 about:blank 的 onLoadingStateChange(true) 可能晚于 loadUrl 调用送达（volatile 仍为 false）→ 直接 loadURL 被丢弃。**必须配 readyForNav**。教训36。
5. **此版本 CefRequestHandler API 变更**：`onBeforeResourceLoad` 移到 `getResourceRequestHandler()` 返回的 `CefResourceRequestHandler.onBeforeResourceLoad`（返回 Boolean，true=取消）；`onBeforeBrowse` 5 参（无 transitionType）；SSL 用 `onCertificateError(...): Boolean` + callback.Continue()；`CefPostDataElement.setToBytes(int count, byte[])`；`CefBrowser` 无 dispose（用 close(true)）；`doMessageLoopWork(long)`。
6. **JS 桥同步语义**：cefQuery 是异步的，无法完全复刻 addJavascriptInterface 的同步返回；`getFromMemory` 用页面 `_memData` 对象实现同步（JSBridgeResult 协议要求）；其余方法返回值为异步（已知偏差，文档化）。
7. **测试 URL 断言双斜杠**：pageUrl 以 '/' 结尾，`"$pageUrl/img.png"` 拼出 `//img.png` 导致断言误报——用 `pageUrl.trimEnd('/') + "/img.png"`。

### 14.3 已验证有效的方法（照用）
- **隐藏窗口承载 JCEF**：非 OSR + 1x1 无装饰 Frame（setLocation(-10000,-10000)）由 EDT 驱动——探针/冒烟全部通过，无需手动 doMessageLoopWork。
- **JS 往返模板**：CefMessageRouter + `window.cefQuery`；evaluateJavascript 自包含注入 `window.__legadoEval`（不依赖 onLoadStart 注入，杜绝注入丢失）；结果经 cefQuery 编码回 Java，pendingEval(ConcurrentHashMap<Long, cb>) 按 id 分发。
- **JS 桥模板**：addJavascriptInterface(bridge, name) 存 map → onLoadStart 注入 `window.<name>=new Proxy(...)`（get 陷阱返回函数，参数序列化 btoa(JSON)）→ Java onQuery 反射 method.invoke；args 按参数类型转换（Number/Boolean/Array<String?>）。
- **cache 同步**：evaluateJavascript 命中 `window.<JSBridgeResult>('id', bool)` 正则时，从 CacheManager.getFromMemory(id) 取数据前置注入 `window.<nameCache>._memData[id]`，实现 JSBridgeResult 内 `cache.getFromMemory(id)` 同步读。
- **bundle 就绪判定**：bundleDir.listFiles() any { name startsWith "libcef" or "jcef" }。
- **Windows 验证**：cmd /c 原生重定向 + [IO.File]::ReadAllText(file, UTF8) 读回；断言数 [regex]::Matches(raw, '\[PASS\]').Count。

### 14.4 当前状态（2026-08-11 会话结束时）
- Part 0/1/2/3/4/5/6 done + **Part 7 引擎层 T7.0~T7.5 done**（--webview-smoke-test 15 [PASS] 连跑 3 次全绿；无 bundle 降级 [SKIP] 不失败；--dao/--net 无回归），STATUS.json 已同步（lessons 追加 34~36；T7.0 置 done）
- remote main 最新：本会话提交后更新
- 下一步：**Compose Multiplatform 前端**（T7.6 骨架 → T7.7 书架/书源/阅读 → T7.8 前端 WebView 集成 + Part7 联测），见 PLAN.md 第 7.5 节
