-- 作者：yangleduo
-- =============================================================================
-- V42: 敏感词总开关 + 客服联系方式
-- =============================================================================

ALTER TABLE `sys_runtime_setting`
  ADD COLUMN `sensitive_filter_enabled` TINYINT(1) NOT NULL DEFAULT 1
    COMMENT '敏感词过滤总开关' AFTER `max_upload_bytes`,
  ADD COLUMN `support_email` VARCHAR(128) DEFAULT NULL
    COMMENT '客服邮箱' AFTER `sensitive_filter_enabled`,
  ADD COLUMN `support_phone` VARCHAR(64) DEFAULT NULL
    COMMENT '客服电话' AFTER `support_email`;
