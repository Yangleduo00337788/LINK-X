-- 作者：yangleduo
-- =============================================================================
-- V38: 自定义组织数据范围（角色 data_scope=4 + sys_role_dept）
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_role_dept` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `role_id` BIGINT NOT NULL COMMENT '角色 ID',
  `dept_id` BIGINT NOT NULL COMMENT '部门 ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_dept` (`role_id`, `dept_id`),
  KEY `idx_role_dept_dept` (`dept_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色自定义数据范围部门';

ALTER TABLE `sys_role`
  MODIFY COLUMN `data_scope` TINYINT NOT NULL DEFAULT 1
    COMMENT '数据范围：1全部 2本人 3本部门及下级 4自定义组织';
