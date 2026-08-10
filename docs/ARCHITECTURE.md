# 移植架构说明

## 目标

Legado（Android）的完整后端引擎 → 纯 JVM Kotlin 服务，前端完全解耦。

## 源码来源与移植方式

- 源：`/workspace/legado`（LegadoTeam/legado，commit 36d58eea，2026-08-10）
- 方式：从 `legado/app/src/main/java/io/legado/app/` 复制后端相关包，逐文件去除 Android 依赖。
- 后端相关源码规模：约 230~250 个文件（data / model / help / api / web / constant / exception / utils 子集）。
- 移植后包名改为 `io.legado.desktop.*`，类名/逻辑保持原样以便对照上游。

## 需要替换的 Android 依赖

| Android 依赖 | 桌面替代 | 涉及范围 |
|---|---|---|
| `splitties.appctx`（全局 Context） | `env/DesktopEnv`（数据目录 + 配置） | ~60-80 文件，机械替换 |
| `SharedPreferences` | JSON 配置文件（DesktopEnv） | 同上 |
| Room（24 DAO / 44 实体 / schema v99） | sqlite-jdbc + 手写 DAO 实现 | 数据层，工作量最大 |
| `android.util.Base64` | `java.util.Base64` | 少量 |
| `android.text.TextUtils` | `kotlin.text` | 少量 |
| `Dispatchers.Main` | kotlinx-coroutines-swing | help/coroutine 等 |
| Glide / Bitmap（封面、正文图） | OkHttp 拉取 + ImageIO | api/BookController |
| `android.webkit.*`（Cookie/WebView） | 纯 HTTP Cookie 或禁用 | help/http |
| Android TTS/音频/视频 | **禁用（明确不移植）** | — |

## 明确不移植的功能

见根 README「明确不移植」。核心原则：只保留"读小说/RSS/书源管理"后端能力。

## 前端解耦原则

1. 后端无任何 UI 代码，无 HTML 模板伺服（静态资源伺服由前端自行处理）。
2. 所有交互走 `docs/API.md` 契约：REST JSON + WebSocket +（可选）MCP。
3. CORS 放开（后端开发期 `Access-Control-Allow-Origin: *`），前端可用任意域名/端口开发。
4. 后端监听默认 `127.0.0.1:2323`，端口可用 `--port` 参数/环境变量覆盖。

## 数据目录

- 默认 `~/.legado-desktop/`（环境变量 `LEGADO_DESKTOP_HOME` 可覆盖）
- 内容：`config.json`（偏好）、`books.db`（SQLite）、`cache/`、`books/`（本地书籍）、`covers/`
