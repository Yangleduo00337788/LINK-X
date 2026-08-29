<!-- 作者：yangleduo -->
# LinkX Website

LinkX 产品官网静态站点（首页、文档、版本日志、法律页、帮助中心），部署至 Cloudflare Pages，自定义域 `mars-studio.asia`。

完整说明见仓库根目录 **[README.md](../README.md)** 与 **[9.3 产品官网](../README.md#93-产品官网cloudflare-pages)**。

## 本地预览

```bash
npx serve .
```

浏览器访问终端提示的本地地址（通常为 http://localhost:3000）。

## 部署（Cloudflare Pages）

1. Cloudflare Dashboard → Workers 和 Pages
2. 上传本目录内全部文件（无需构建步骤）
3. 绑定自定义域 `mars-studio.asia`

## 主要页面

| 页面 | 路径 |
|------|------|
| 首页 | `index.html` |
| 文档 | `docs.html` |
| 版本日志 | `changelog.html` |
| 隐私政策 | `legal/privacy.html` |
| 服务协议 | `legal/service.html` |
| 帮助中心 | `help/index.html` |

`docs.html` 含灵伴 Agent、版本与自动更新、消息落库加密、部署说明与 FAQ；`legal/privacy.html` 信息安全章节同步说明可选落库加密。

## 版本日志与下载链接

客户端下载与 changelog 内容由以下文件维护，**发版或调整版本规划时须与仓库根目录 `CHANGELOG.md` 及管理端版本发布记录保持一致**：

| 文件 | 用途 |
|------|------|
| `shared/site-config.js` | **后端 API 根地址**（含 `/api`）；复制自 `site-config.example.js` |
| `shared/app-download.js` | 首页 / 版本日志下载按钮，优先 R2 公网直链（`GET /app/version`） |
| `shared/changelog-data.js` | 版本规划（`roadmap`）、各平台 release 历史（**不再硬编码 OSS 下载地址**） |
| `main.js` / `changelog.js` | 平台切换与下载按钮绑定 |
| `docs.html` / `docs.js` | 文档「产品概述」中的平台与版本规划说明 |

### Windows 下载链路

1. 管理端「版本发布」上传安装包 → 对象存储 `releases/...`（R2 等）
2. 管理端配置 R2 公开域名（`pub-xxx.r2.dev`）后，安装包具备永久公网直链
3. 官网 `site-config.js` 配置 `apiBaseUrl`；可选 `installerDirectUrl` 作为 API 不可用时的兜底
4. 用户点击下载 → 优先使用 `/app/version` 返回的 R2 直链；失败时回退 `installerDirectUrl` 或 `GET /app/installer`

部署生产环境前请将 `shared/site-config.js` 中的 `apiBaseUrl` 改为线上后端地址，并在服务端 `CORS_ALLOWED_ORIGINS` 加入 `https://mars-studio.asia`（用于拉取版本号与下载地址）。

### 版本规划（与 CHANGELOG.md 同步）

| 版本 | 主题 |
|------|------|
| **1.1.0** | Linux 桌面端、Android 移动端 |
| **1.2.0** | 灵伴知识库与 Agent 策略、本地搜索与消息同步、短视频推荐与运营 |

当前 Windows 稳定版示例：

- 版本：`1.0.1`
- 安装包：`LinkX-Installer-1.0.1.exe`

更新后重新上传部署即可；客户端法律页 / 帮助中心 URL 默认指向本域，一般无需重新打包客户端。

## 帮助中心目录

帮助文章目录由 `help/scripts/build-catalog.mjs` 生成，输出至 `help/data/catalog.*.js`。
