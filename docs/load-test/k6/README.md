<!-- 作者：yangleduo -->
# LinkX k6 压测文档 / k6 load-test docs

脚本在 `linkx-server/k6/`。本目录只放**书面报告**。

## 报告列表 / Reports

| 日期 Date | 档位 Profile | 结果 Result | 路径 Path |
|-----------|--------------|-------------|-----------|
| 2026-11-06 | load | 7/7 通过 | [2026-11-06/](./2026-11-06/) |
| 2026-11-06 | gradient 50→200 VU | 3/3 通过 | [2026-11-06-gradient/](./2026-11-06-gradient/) |
| 2026-11-07 | stress **1000 VU** × 2m | 5/6 通过（auth-load 失败）；三类现象非 bug，生产靠限流 + 多实例 | [2026-11-07-1000vu/](./2026-11-07-1000vu/) |
| 2026-11-07 | write-path **200 VU** | 仅历史消息过线；发消息/注册/红包/通话/上传未过 | [2026-11-07-200vu-write/](./2026-11-07-200vu-write/) |
| 2026-11-07 | write-path **200 VU 重跑** | 雪花 ID/风暴已修；通话红包发消息上传业务过；注册仍 BCrypt；上传 p95 慢 | [2026-11-07-200vu-write-rerun/](./2026-11-07-200vu-write-rerun/) |

跑法见 `linkx-server/k6/README.md`。压测必须用 `SPRING_PROFILES_ACTIVE=k6`，不要打 local/prod。
