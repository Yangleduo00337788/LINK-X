# LinkX 安全测试说明

## 自动化（CI）

| 能力 | 实现 |
|------|------|
| SAST | GitHub CodeQL（Java） |
| 依赖审查 | `actions/dependency-review-action`（PR，high 失败） |
| OSV | `google/osv-scanner-action` 扫描 server/client/admin 依赖（告警，不阻断） |
| npm SCA | `npm audit --audit-level=high`（client + admin，告警） |
| 应用层用例 | `ProductionSecurityValidatorTest`、`ProductionSecretRulesTest`、`AdminSecurityIT`、`AdminDeviceSecurityIT`、`AdminRateLimitIT`、`AdminStepUpIT` |
| DAST | ZAP baseline：CI 内起 test-profile 服务，扫公开 `/api/health` + [`urls-public.txt`](../../linkx-server/perf/zap/urls-public.txt) curl 门禁；详见 [`linkx-server/perf/zap/README.md`](../../linkx-server/perf/zap/README.md) |

## 本地命令

```bash
# 后端安全相关单测
cd linkx-server
mvn -B "-Dtest=ProductionSecurityValidatorTest,ProductionSecretRulesTest,AdminSecurityIT,AdminRateLimitIT" test

# npm 审计
cd linkx-client && npm audit --audit-level=high
cd linkx-admin && npm audit --audit-level=high

# 公开面可达性
bash linkx-server/perf/zap/check-public-urls.sh http://127.0.0.1:8080/api
```

## 不在默认范围

- SonarCloud（需外部账号）
- 已认证 ZAP 全站爬取
- 渗透测试报告（人工安全窗口）

## 响应建议

- Critical/High 依赖：阻断合并或 7 日内升级
- Medium：记入 issue，下个迭代处理
- ZAP baseline 噪声：加入规则忽略前需安全负责人确认
