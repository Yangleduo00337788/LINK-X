-- =============================================================================
-- V43: 管理端异步导出任务表
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_admin_export_job` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  `requester_id` BIGINT NOT NULL COMMENT '发起人用户ID',
  `module` VARCHAR(32) NOT NULL COMMENT '导出模块：users/devices/...',
  `query_json` TEXT DEFAULT NULL COMMENT '筛选条件 JSON',
  `status` VARCHAR(16) NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/EXPIRED',
  `row_count` INT DEFAULT NULL COMMENT '导出行数',
  `file_name` VARCHAR(128) DEFAULT NULL COMMENT '下载文件名',
  `content_bytes` LONGBLOB DEFAULT NULL COMMENT 'CSV 内容（成功后填充）',
  `error_message` VARCHAR(512) DEFAULT NULL COMMENT '失败原因',
  `expire_at` DATETIME DEFAULT NULL COMMENT '过期时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_admin_export_requester` (`requester_id`, `create_time`),
  KEY `idx_admin_export_status` (`status`, `create_time`),
  KEY `idx_admin_export_expire` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理端异步导出任务';
