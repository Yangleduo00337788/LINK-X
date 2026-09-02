-- 作者：yangleduo
-- =============================================================================
-- V125: 审计日志哈希链（防篡改）
--   sys_audit_log 增加两列：
--     prev_hash：前一行 log_hash（链式锚点），首行取固定种子；
--     log_hash ：本行哈希 = SHA256( 逐字段SHA256拼接 + prev_hash )。
--   为保持 SQL/Java 哈希口径完全一致，历史数据回填不在此处用 SQL 计算，
--   由 AuditLogServiceImpl#backfillChain（启动时）用同一算法重算整条链。
-- =============================================================================

ALTER TABLE `sys_audit_log`
  ADD COLUMN `log_hash` VARCHAR(64) DEFAULT NULL COMMENT '本行哈希（链式，SHA-256，防篡改）' AFTER `extra_data`,
  ADD COLUMN `prev_hash` VARCHAR(64) DEFAULT NULL COMMENT '前一行 log_hash（链式锚点）' AFTER `log_hash`;