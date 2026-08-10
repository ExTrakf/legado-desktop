# frontend（占位）

前端完全独立开发，本目录只是占位。

## 约定

- 与后端只通过 `../docs/API.md` 的接口契约通信（REST JSON + WebSocket）
- 后端默认监听 `http://127.0.0.1:2323`，开发期 CORS 全开
- 打包方案（Tauri/Electron）由你决定；本仓库不绑定任何前端框架

## 建议

- 直接 `pnpm create vite` 之类脚手架起步
- 阅读器、书架、书源管理页面全部自建
- 参考 Legado 原版 Android UI 交互设计，但代码完全重写
