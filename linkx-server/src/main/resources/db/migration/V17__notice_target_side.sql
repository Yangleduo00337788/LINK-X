-- =============================================================================
-- V17: 公告区分管理端 / 客户端目标，两端不互通
-- =============================================================================

ALTER TABLE `sys_admin_notice`
  ADD COLUMN `target_side` VARCHAR(16) NOT NULL DEFAULT 'client'
    COMMENT 'admin=仅管理端 / client=仅客户端' AFTER `content`;

UPDATE `sys_admin_notice` SET `target_side` = 'client' WHERE `target_side` IS NULL OR `target_side` = '';

ALTER TABLE `sys_admin_notice`
  ADD KEY `idx_notice_target_side` (`target_side`);
