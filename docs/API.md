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
- [ ] `GET /getBookshelf` — 书架
- [ ] `POST /saveBook` `POST /deleteBook` — 增删书
- [ ] `GET /getChapterList?url=xxx` — 目录
- [ ] `GET /getBookContent?url=xxx&index=N` — 正文
- [ ] `GET /cover?path=xxx` `GET /image?...` — 图片
- [ ] `POST /saveBookProgress` — 阅读进度
- [ ] `GET/POST /getBookSource(s) /saveBookSource(s) /deleteBookSources` — 书源管理
- [ ] `GET/POST /getRssSource(s) /saveRssSource(s) /deleteRssSources` — RSS 源
- [ ] `GET/POST /getReplaceRules /saveReplaceRule /deleteReplaceRule /testReplaceRule` — 替换规则
- [ ] `WS /searchBook` — 多源搜索
- [ ] `WS /bookSourceDebug` `WS /rssSourceDebug` — 源调试
- [ ] MCP `/mcp`（Streamable HTTP，Ktor）— 可选

## WebSocket 协议

- 搜索：`{ "key": string }` → 持续推送结果，结束帧为特定状态
- 调试：`{ "key": string, "tag": string }` → 步骤日志推送
- 鉴权：`Sec-WebSocket-Protocol: legado, legado.token.<token>`（若启用令牌）

## 安全

- 默认仅监听 127.0.0.1
- 书源写入/调试接口支持 `X-Legado-Token`（Web 书源访问令牌，默认关闭）
