# 前后端接口契约（草案）

后端只通过以下方式与前端通信。契约移植自 Legado `api.md` + `api/controller/`，
随移植进度逐步补全并标注状态。

## 基础约定

- Base URL：`http://127.0.0.1:2323`（可配置）
- 响应包装：`{ "isSuccess": bool, "errorMsg": string, "data": T | null }`
- 字符集：UTF-8
- CORS：开发期全开

## 状态

- [x] `GET /api/health` — 存活检查（新建）
- [x] `GET /getBookshelf` — 书架
- [x] `POST /saveBook` `POST /deleteBook` — 增删书
- [x] `GET /getChapterList?url=xxx` — 目录
- [x] `GET /getBookContent?url=xxx&index=N` — 正文
- [x] `GET /cover?path=xxx` `GET /image?...` — 图片（封面/正文图字节）
- [x] `POST /saveBookProgress` — 阅读进度
- [x] `GET/POST /getBookSource(s) /saveBookSource(s) /deleteBookSources` — 书源管理
- [x] `GET/POST /getRssSource(s) /saveRssSource(s) /deleteRssSources` — RSS 源
- [x] `GET/POST /getReplaceRules /saveReplaceRule /deleteReplaceRule /testReplaceRule` — 替换规则
- [x] `GET/POST /getHttpLogs /getHttpLog` — HTTP 日志（令牌保护）
- [x] `POST /saveReadConfig` `GET /getReadConfig` — Web 阅读配置
- [x] `POST /saveJsSource` — JS 书源导入（text/plain + 令牌）
- [x] `POST /restoreDefaultData` — 恢复默认数据（txtTocRule/dictRule/rssSource/httpTTS，可选 body {"types":[...]}）
- [x] `POST /cacheBook` `POST /cacheBookStop` `POST /cacheBookRemove` — 缓存书籍（批量缓存章节正文）
- [x] `WS /searchBook` — 多源搜索
- [x] `WS /bookSourceDebug` `WS /rssSourceDebug` — 源调试

**桌面新增（T7.7/T7.8 前端集成 + 上游同步）：**

- [x] `POST /setJsSourceToken` — 运行时设置/清除 Web 书源令牌（body `{"token":"..."}`，空串=清除；**无需令牌**，仅监听 127.0.0.1；等价 CLI `--set-js-source-token`）
- [x] `GET /getJsSourceApiTokenRequired` — 查询令牌是否必填（返回 bool，无缓存）
- [x] `GET /getCookies` — 全部持久化 Cookie 列表（令牌保护）
- [x] `POST /setCookie` — 写入/更新 Cookie（body `{"url","cookie"}`，cookie 为完整 `k=v; k2=v2` 串，令牌保护）
- [x] `POST /clearCookies` — 清除 Cookie（body `{"url"}`；url 空 = 清空全部，令牌保护）
- [x] `GET /getBookGroups` — 全部书籍分组（groupId 位标记，前端书架分组用）

## 路由细节

HTTP 服务端口默认 `2323`，WebSocket 服务为 `2323 + 1 = 2324`（原版 `port + 1` 约定）。
令牌受保护的路由（`x-legado-token` 请求头；WebSocket 用 `Sec-WebSocket-Protocol: legado, legado.token.<token>`）：

- **写路由**：`/saveBookSource(s)` `/deleteBookSources` `/saveRssSource(s)` `/deleteRssSources`
  `/saveReplaceRule` `/deleteReplaceRule` `/testReplaceRule` `/restoreDefaultData` `/setCookie` `/clearCookies`
- **读路由**：`/getHttpLogs` `/getHttpLog` `/getCookies`

**令牌语义（上游同步）**：`jsSourceApiTokenRequired` 默认 `true` —— 令牌**必填**，未配置/不匹配即拒绝
（返回"Web 书源访问令牌未配置或不正确"）。如需本地单机免令牌，在数据目录
`<home>/config/config.json`（Windows `C:\Users\<你>\.legado-desktop\config\config.json`）加
`"jsSourceApiTokenRequired": false`，则写/读路由与 WS 均不再校验令牌（WS 子协议仅需 `legado`）。
`/setJsSourceToken` 无需令牌（仅监听 127.0.0.1，用于配置初始令牌，避免锁死）。
`/saveBook` `/deleteBook` `/saveBookProgress` `/cacheBook*` `/addLocalBook` `/saveReadConfig` 不设令牌保护。

未移植（原版存在但裁剪）：评论相关路由（`/openLegacyReview` `/runLegacyReview` `/legacyReviewPage`
`/getReviewSummary` `/getReviewDetail` `/getReviewReplies`）、静态资源伺服（官方 web UI，前端分离）。

已知原版行为（忠于原版未改）：`/saveReplaceRule` `/deleteReplaceRule` 成功路径不设置
`isSuccess`（恒为 `false`），客户端以 `/getReplaceRules` 列表核对生效状态。

## WebSocket 协议

- 搜索：`{ "key": string }` → 持续推送结果，结束帧为特定状态
- 调试：`{ "key": string, "tag": string }` → 步骤日志推送
- 鉴权：令牌必填时需 `Sec-WebSocket-Protocol: legado, legado.token.<base64url(token)>`（与 `x-legado-token` 同一令牌）；
  `jsSourceApiTokenRequired=false` 时仅需子协议 `legado`

## 安全

- 默认仅监听 127.0.0.1
- 书源写入/调试接口用 `X-Legado-Token`（Web 书源访问令牌）：**默认必填**（`jsSourceApiTokenRequired=true`）；
  可在 `<home>/config/config.json` 置 `false` 关闭校验；令牌配置入口 = 前端连接页"应用令牌到后端"
  （`POST /setJsSourceToken`）或 CLI `--set-js-source-token`
