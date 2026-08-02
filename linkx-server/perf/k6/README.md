# LinkX k6 压测

## 前置

- 安装 [k6](https://k6.io/docs/get-started/installation/)
- 本机可访问已启动的 `linkx-server`（默认 `http://127.0.0.1:8080/api`）
- 准备压测账号（或使用种子用户）

## 全 API（OpenAPI 驱动）

```bash
# 1) 导出 OpenAPI 并生成 endpoints.json
./scripts/export-openapi.sh http://127.0.0.1:8080/api

# 2) 只读全扫（推荐先跑）
k6 run -e BASE_URL=http://127.0.0.1:8080/api -e USER=demo -e PASS='***' \
  -e VUS=10 -e DURATION=2m full-api.js

# 3) 含写操作（空 JSON；业务 4xx 不计失败；见下方护栏）
k6 run -e INCLUDE_MUTATING=1 -e USER=demo -e PASS='***' \
  -e VUS=5 -e DURATION=60s full-api.js

# 4) 逐端点扫一次，列出 5xx（建议写压测前先跑）
INCLUDE_MUTATING=1 USER=demo PASS='***' node scan-once.mjs
```

未生成 `endpoints.json` 时回退 [`endpoints.sample.json`](endpoints.sample.json)。

发消息主路径是 WebSocket（`:8081/ws`），不在 REST 全扫内。

### 写操作护栏（`lib/guards.js`）

开启 `INCLUDE_MUTATING=1` 时：

- 路径占位使用高位哨兵 ID（`900000000000000001`），降低误伤真实资源
- **永久跳过**会清账号/登出/封禁等危险写接口，例如：
  - `POST /compliance/purge`、`POST /user/delete-account`
  - `POST /auth/logout`、`POST /admin/auth/logout`（避免压测中途 token 失效）
  - `POST /auth/reset-password*`、`POST /admin/users/{id}/ban|unban|reset-password`
  - `DELETE /notifications/clear`、设备 kick/ban/revoke
- 写请求 body 固定为 `{}`；缺参/无权限的 **4xx 算通过**，仅 **5xx/网络失败** 计入失败率

## 热路径

```bash
k6 run -e BASE_URL=http://127.0.0.1:8080/api -e USER=demo -e PASS='***' \
  -e VUS=10 -e DURATION=1m scenarios/hot-path.js
```

阈值默认：`http_req_failed < 1%`，`p95 < 500ms`（全 API：失败率 `<5%`，`p95 <1500ms`）。

## 无 k6 时用 Node 运行器

本机无法安装 k6 时可用：

```bash
# 热路径
BASE_URL=http://127.0.0.1:8080/api USER=demo PASS='***' VUS=8 DURATION=45s \
  node run-node-load.mjs hot-path

# 全 API 只读
BASE_URL=http://127.0.0.1:8080/api USER=demo PASS='***' VUS=5 DURATION=60s \
  node run-node-load.mjs full-api

# 全 API 含写
INCLUDE_MUTATING=1 USER=demo PASS='***' VUS=5 DURATION=60s \
  node run-node-load.mjs full-api
```

## CI

`load-smoke` job 运行 `node scripts/validate-scripts.js` 做静态校验。

完整压测见 [nightly-load](../../../.github/workflows/nightly-load.yml)：

- **定时**：配置 `LOAD_*` secrets 后自动跑 hot-path + 全 API 只读
- **手动** `workflow_dispatch`：`run_hot_path` / `run_full_api`（含写）

## 环境变量

| 变量 | 说明 |
|------|------|
| `BASE_URL` | API 根，默认 `http://127.0.0.1:8080/api` |
| `USER` / `PASS` | 登录账号 |
| `VUS` / `DURATION` | 并发与时长 |
| `INCLUDE_MUTATING` | `1` 包含写操作（受护栏约束） |
| `MAX_ENDPOINTS` | 限制扫描数量 |
