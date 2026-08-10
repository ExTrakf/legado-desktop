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
