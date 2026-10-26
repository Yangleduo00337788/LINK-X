# LinkX 静态功能演示站（linkx-demo）

一比一复用仓库内 `linkx-admin` 与 `linkx-client` 的真实 UI，通过本地 MOCK 拦截所有 API，适合部署到 **Cloudflare Pages** 做产品演示。

## 结构

| 路径 | 说明 |
|------|------|
| `/` | 浅色电脑屏 + 内嵌 C 端客户端；右侧按钮进入 B 端 |
| `/admin/` | 管理后台（直接网页） |
| `/client/` | 桌面客户端（Hash 路由，供 iframe 加载） |

## 本地开发

```bash
cd linkx-demo
npm install
npm run dev
```

- 演示首页（C 端电脑屏）：http://127.0.0.1:5200/
- 管理后台：http://127.0.0.1:5201/admin/
- 客户端：http://127.0.0.1:5202/client/

> 需先在 `linkx-admin`、`linkx-client` 目录执行过 `npm install`（复用其依赖）。

## 演示图片资源

首次或更新素材时下载真实图片到 `public/demo-media`（门户 / 客户端 / 管理端共用）：

```bash
cd linkx-demo
npm run demo:media
```

## 构建

```bash
cd linkx-demo
npm run demo:media   # 可选，确保图片已就位
npm run build
```

产物在 `linkx-demo/dist/`：

- `index.html` — 门户
- `admin/` — B 端
- `client/` — C 端

本地预览完整站点：

```bash
npx serve dist -l 4173
```

## Cloudflare Pages

1. 创建 Pages 项目，构建命令：`cd linkx-demo && npm install && npm run build`
2. 构建输出目录：`linkx-demo/dist`
3. `portal/public/_redirects` 已配置 SPA 回退（含 `/admin/*`、`/client/*`）

## 演示登录

- **管理后台**：任意用户名 + 任意密码（已关闭验证码 / 签名 / 加密 MOCK）
- **客户端**：建议 `demo` + 任意密码

所有写操作仅在前端提示成功，不会调用真实后端。
