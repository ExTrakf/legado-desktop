# Legado Desktop

将 [Legado（开源阅读）](https://github.com/LegadoTeam/legado) 的**完整后端引擎**移植到桌面 JVM 的独立项目。
前端完全独立开发（不沿用官方 Web UI），通过 HTTP/WebSocket API 与后端通信。

> 许可证：GPL-3.0（派生自 Legado，必须保持开源）

## 架构

```
┌──────────────────────────────────────────────┐
│  frontend/（你的前端，完全自由）                │
│  - 可以是任何技术栈：Vue/React/Tauri/Electron  │
└──────────────┬───────────────────────────────┘
               │ HTTP (REST + JSON) / WebSocket / MCP
┌──────────────▼───────────────────────────────┐
│  backend/（纯 JVM Kotlin 服务，本仓库维护）     │
│  - 规则引擎（正则/XPath/JSONPath/CSS/JS 书源）  │
│  - Rhino JS 引擎（htmlunit-core-js fork）     │
│  - OkHttp 网络层 + Cookie/代理                 │
│  - SQLite 数据层（schema 对齐 Legado v99）     │
│  - NanoHTTPD HTTP 服务 + WebSocket + MCP      │
│  - 本地书籍解析（TXT/EPUB/MOBI/UMD）           │
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
    Main.kt               # 入口：启动/关闭服务
    env/                  # 桌面环境抽象（替代原 Android Context/appCtx）
    data/                 # SQLite DAO 层（移植自 Room DAO）
    core/                 # 引擎（从 legado app 模块移植）
    api/                  # HTTP/WebSocket 控制器
docs/
  ARCHITECTURE.md         # 移植架构说明
  API.md                  # 前后端接口契约
frontend/
  README.md               # 前端说明（你自建）
```

## 移植状态

- [x] 项目骨架、构建链、最小 HTTP 服务（/api/health）
- [x] 数据层：SQLite DAO（Room → sqlite-jdbc，**24/24 DAO 完成**，schema 对齐 Legado v99）
- [ ] 规则引擎：AnalyzeRule / jsSource / Rhino
- [ ] 网络层：OkHttp + Cookie + 代理
- [ ] 书源管理 API：save/get/delete/调试 WS
- [ ] 搜索/目录/正文 API + WebSocket 搜索
- [ ] 本地书籍解析（TXT/EPUB/MOBI/UMD）
- [ ] RSS、替换规则、MCP
- [ ] 数据库 schema 与 Legado 备份导入兼容

> 数据层细节：24 张实体表 + `book_sources_part` 视图（schema v99）；DAO 接口与 Legado 一致，
> SQL 逐条对照原版 Room `@Query`；`--dao-smoke-test` 全量冒烟（24 DAO CRUD + flow +
> collate localized + IN(:list) 展开）由 `tools/test_backend.sh` 集成验证。

## 明确不移植（初版禁用）

- TTS / 朗读 / 音频播放（含 help/audio、AudioPlay、ReadAloud 等）
- 视频播放 / 弹幕（VideoPlay、gsyVideoPlayer）
- Android UI / 通知 / 前台服务 / 广播 / 桌面部件 / ContentProvider
- WebView 依赖功能（部分书源 WebView 登录、BackstageWebView）
- 应用内更新 / 崩溃统计（Firebase）
