# 管理端 API 集合（Postman / Apifox）

## 文件

| 文件 | 说明 |
|------|------|
| `linkx-admin.postman_collection.json` | 管理端 Postman Collection v2.1，**Apifox 可直接导入** |
| 全量 OpenAPI | `../api/linkx-openapi.json` |
| 全量导入指南 | [../api/APIFOX_IMPORT.md](../api/APIFOX_IMPORT.md) |

## 重新生成

```bash
node linkx-server/perf/k6/scripts/generate-postman-collection.mjs --all
```

或仅管理端：

```bash
node linkx-server/perf/k6/scripts/generate-admin-postman.mjs
```

在更新后端 Swagger/OpenAPI 后执行，保持集合与现网一致。

## 导入 Apifox

完整步骤见 **[docs/api/APIFOX_IMPORT.md](../api/APIFOX_IMPORT.md)**。

简要步骤：

1. 导入 OpenAPI：`docs/api/linkx-openapi.json`
2. 导入 Postman：`linkx-admin.postman_collection.json` 或全量 `linkx-smoke-scenarios.postman_collection.json`
3. 导入环境：`docs/api/linkx-apifox-environment.json`
4. 填写 `adminPassword`，运行冒烟场景

## 覆盖范围

仅包含 `/admin/**` 路径（约 159 个操作），不含客户端 `/auth`、`/chat` 等。

全量（客户端 + 管理端）见 `docs/api/linkx-full.postman_collection.json`。
