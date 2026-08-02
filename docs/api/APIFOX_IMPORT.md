# LinkX API → Apifox 导入指南

> 全量 **381** 个 REST 操作（客户端 222 + 管理端 159），含 IT 对齐的断言脚本与冒烟场景。

## 文件清单

| 文件 | 说明 |
|------|------|
| `linkx-openapi.json` | OpenAPI 3.1 全量定义（**推荐先导入**） |
| `linkx-full.postman_collection.json` | 全量 Postman 集合（client + admin 分目录） |
| `linkx-client.postman_collection.json` | 仅客户端接口 |
| `../admin/linkx-admin.postman_collection.json` | 仅管理端接口 |
| `linkx-smoke-scenarios.postman_collection.json` | **冒烟场景**（多步流程 + 断言） |
| `linkx-apifox-environment.json` | 环境变量模板 |
| `import-to-apifox.mjs` | CLI 一键导入脚本 |

源 OpenAPI：`linkx-server/perf/k6/openapi.json`

## 重新生成

更新后端 Swagger 后执行：

```bash
# 1. 从运行中的服务导出最新 OpenAPI（可选）
bash linkx-server/perf/k6/scripts/export-openapi.sh http://127.0.0.1:8080/api

# 2. 生成全部 Apifox/Postman 产物
node linkx-server/perf/k6/scripts/generate-postman-collection.mjs --all
```

## 方式一：Apifox 图形界面导入（推荐）

### 步骤 1 — 导入接口定义

1. Apifox → **项目** → **导入** → **OpenAPI/Swagger**
2. 选择 `docs/api/linkx-openapi.json`
3. 导入选项建议：
   - **覆盖已有接口**（或合并到根目录）
   - 开启 **自动生成测试用例**（如有该选项）

### 步骤 2 — 导入测试集合与断言

1. **导入** → **Postman**
2. 依次导入：
   - `linkx-full.postman_collection.json`（全量接口 + 后置脚本）
   - `linkx-smoke-scenarios.postman_collection.json`（冒烟场景）

### 步骤 3 — 导入环境

1. **环境** → **导入**
2. 选择 `linkx-apifox-environment.json`
3. 填写变量：

| 变量 | 说明 |
|------|------|
| `baseUrl` | 如 `http://localhost:8080/api` |
| `adminUsername` | 管理员账号（默认 `admin`） |
| `adminPassword` | 管理员密码 |
| `clientUsername` | 客户端测试用户 |
| `clientPassword` | 客户端测试密码 |
| `accessToken` | 管理端 Token（登录后自动写入） |
| `clientAccessToken` | 客户端 Token（登录后自动写入） |

### 步骤 4 — 运行冒烟

1. 打开 **自动化测试**
2. 选择集合 **LinkX Smoke Scenarios**
3. 选择环境 **LinkX Local**
4. 运行全部场景

## 方式二：Apifox CLI 一键导入

### 安装 CLI

参考 [Apifox CLI 安装指南](https://apifox.com/apifox-cli-installation-guide.md)

### 配置凭证

Apifox → **项目设置** → **开放 API** → 获取 **项目 ID** 与 **Token**

```powershell
$env:APIFOX_PROJECT_ID = "你的项目ID"
$env:APIFOX_ACCESS_TOKEN = "你的Token"
node docs/api/import-to-apifox.mjs
```

仅导入冒烟场景：

```powershell
node docs/api/import-to-apifox.mjs --only smoke
```

## 测试覆盖说明

断言脚本与 `linkx-server` 集成测试对齐：

| 场景 | 来源 IT | 断言 |
|------|---------|------|
| 客户端公开读 | `ClientReadApiSuccessIT` | HTTP 200 + `code=200` |
| 客户端登录后读 | `ClientReadApiSuccessIT` | HTTP 200 + `code=200` |
| 客户端热路径 | `ClientHotPathSuccessIT` | HTTP 200 + `code=200` |
| 管理端登录后核心 | `AdminRoleSmokeIT` | HTTP 200 + `code=200` |
| 管理端目录扫 | `AdminEndpointPathCatalogIT` | 状态码 &lt; 500 |
| 全量 GET | 默认 | 状态码 &lt; 500 |
| 登录接口 | — | 自动保存 Token 到环境变量 |

冒烟路径配置：`linkx-server/perf/k6/scripts/smoke-paths.json`

## 清理占位符目录 `${openapi.tag.xxx}`

重新导入后，旧占位符目录可能为空但仍显示。CLI 删除需要先在 Apifox 客户端开启：

**项目设置 → 功能设置 → 外部 AI 编辑权限 → 主分支直接编辑权限**

开启后执行：

```powershell
$env:APIFOX_PROJECT_ID = "你的项目ID"
$env:APIFOX_ACCESS_TOKEN = "你的Token"
node docs/api/delete-placeholder-folders.mjs
```

或在 Apifox 界面中手动删除空的 `${openapi.tag.xxx}` 文件夹。

## 整理重复目录（client/admin）

Postman 全量导入可能产生 `client/`、`admin/` 重复树。并行清理（约 2 分钟）：

```powershell
$env:APIFOX_PROJECT_ID = "你的项目ID"
$env:APIFOX_ACCESS_TOKEN = "你的Token"
node docs/api/cleanup-apifox-structure.mjs
```

## 管理端专项文档

- 管理端集合说明：`docs/admin/API_COLLECTION_README.md`
