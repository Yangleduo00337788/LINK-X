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

`docs.html` 含消息落库加密、部署说明与 FAQ（与仓库 README 8.4 对齐）；`legal/privacy.html` 信息安全章节同步说明可选落库加密。

## 版本日志数据

客户端下载与 changelog 内容由 `shared/changelog-data.js` 维护，须与仓库根目录 `CHANGELOG.md` 保持同步。

更新后重新上传部署即可；客户端法律页 / 帮助中心 URL 默认指向本域，一般无需重新打包客户端。

## 帮助中心目录

帮助文章目录由 `help/scripts/build-catalog.mjs` 生成，输出至 `help/data/catalog.*.js`。
