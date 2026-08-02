## Summary

<!-- 变更目的与影响面（1–3 句） -->

## Test plan

- [ ] Server: `cd linkx-server && mvn verify`（JaCoCo 门禁，见 `docs/testing/COVERAGE.md`）
- [ ] Client coverage: `cd linkx-client && npm run test:coverage`
- [ ] Admin coverage: `cd linkx-admin && npm run test:coverage`
- [ ] UI E2E（如涉及前端）: client / admin `npm run test:e2e`
- [ ] 新增/变更 REST 有成功路径自动化（端点矩阵不下降）
- [ ] 权限/鉴权变更已覆盖（Admin\*IT 或角色矩阵）
- [ ] 无新增密钥硬编码；依赖无明显 High/Critical

## Risk notes

<!-- 回滚方式、数据迁移、破坏性变更 -->
