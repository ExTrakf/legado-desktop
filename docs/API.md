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
- [ ] `GET /cover?path=xxx` `GET /image?...` — 图片（路由已挂，功能 T6.2 实现）
- [x] `POST /saveBookProgress` — 阅读进度
- [x] `GET/POST /getBookSource(s) /saveBookSource(s) /deleteBookSources` — 书源管理
- [x] `GET/POST /getRssSource(s) /saveRssSource(s) /deleteRssSources` — RSS 源
- [x] `GET/POST /getReplaceRules /saveReplaceRule /deleteReplaceRule /testReplaceRule` — 替换规则
- [x] `GET/POST /getHttpLogs /getHttpLog` — HTTP 日志（令牌保护）
- [x] `POST /saveReadConfig` `GET /getReadConfig` — Web 阅读配置
- [x] `POST /saveJsSource` — JS 书源导入（text/plain + 令牌）
- [x] `WS /searchBook` — 多源搜索
- [x] `WS /bookSourceDebug` `WS /rssSourceDebug` — 源调试
- [ ] MCP `/mcp`（Streamable HTTP，Ktor）— 可选（T6.3）

## 路由细节

HTTP 服务端口默认 `2323`，WebSocket 服务为 `2323 + 1 = 2324`（原版 `port + 1` 约定）。
书源写路由（`/saveBookSource(s)` `/deleteBookSources` `/saveRssSource(s)` `/deleteRssSources`
`/saveReplaceRule` `/deleteReplaceRule` `/testReplaceRule`）与 HTTP 日志读路由
（`/getHttpLogs` `/getHttpLog`）需要 `x-legado-token` 请求头；WebSocket 需要
`Sec-WebSocket-Protocol: legado, legado.token.<token>`。

未移植（原版存在但裁剪）：评论相关路由（`/openLegacyReview` `/runLegacyReview` `/legacyReviewPage`
`/getReviewSummary` `/getReviewDetail` `/getReviewReplies`）、静态资源伺服（官方 web UI，前端分离）。

已知原版行为（忠于原版未改）：`/saveReplaceRule` `/deleteReplaceRule` 成功路径不设置
`isSuccess`（恒为 `false`），客户端以 `/getReplaceRules` 列表核对生效状态。

## WebSocket 协议

- 搜索：`{ "key": string }` → 持续推送结果，结束帧为特定状态
- 调试：`{ "key": string, "tag": string }` → 步骤日志推送
- 鉴权：`Sec-WebSocket-Protocol: legado, legado.token.<token>`（若启用令牌）

## 安全

- 默认仅监听 127.0.0.1
- 书源写入/调试接口支持 `X-Legado-Token`（Web 书源访问令牌，默认关闭）
