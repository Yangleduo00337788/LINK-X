# OWASP ZAP Baseline（DAST）

对公开面做 baseline 扫描，不登录全站爬取，避免误伤写接口与鉴权流程。

## 本地

```bash
# 需 Docker + 已启动的 linkx-server
docker run --rm -t ghcr.io/zaproxy/zaproxy:stable zap-baseline.py \
  -t http://host.docker.internal:8080/api/health \
  -m 5
```

公开路径清单见 [`urls-public.txt`](urls-public.txt)，可用：

```bash
bash check-public-urls.sh http://127.0.0.1:8080/api
```

建议探测的公开路径：

- `/api/health`
- `/api/health/live`
- `/api/auth/config`
- `/api/auth/captcha`
- `/api/admin/auth/config`
- `/api/app/version`

## CI

[`ci.yml`](../../../.github/workflows/ci.yml) 的 `zap-baseline` job：

1. 启动 Redis service
2. `mvn spring-boot:run`（`test` profile + `useTestClasspath`，端口 8080）
3. 运行 `check-public-urls.sh`（5xx / 连不上则失败）
4. `zaproxy/action-baseline@v0.14.0` 扫 `http://127.0.0.1:8080/api/health`  
   - `fail_action: false`（告警上传 artifact，不因噪声阻断合并）
   - `cmd_options: -m 5`

触发：主干 push 全量；PR 仅在 `security` 路径变更时跑（见 CI 路径分流）。

## 说明

- Baseline 以告警为主；认证后深度扫描（authenticated spider）不在默认流水线内，需单独安全窗口执行。
- 忽略规则需安全负责人确认后再加入 ZAP `rules.tsv`。
