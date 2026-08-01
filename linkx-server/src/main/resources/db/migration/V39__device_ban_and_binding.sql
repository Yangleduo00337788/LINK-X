-- =============================================================================
-- V39: 设备长期封禁 + 用户强制设备绑定
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_device_ban` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户 ID',
  `device_id` VARCHAR(64) NOT NULL COMMENT '设备 ID',
  `reason` VARCHAR(255) DEFAULT NULL COMMENT '封禁原因',
  `status` VARCHAR(16) NOT NULL DEFAULT 'active' COMMENT 'active/released',
  `banned_by` BIGINT DEFAULT NULL,
  `released_by` BIGINT DEFAULT NULL,
  `released_at` DATETIME DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_device_ban_user_device` (`user_id`, `device_id`),
  KEY `idx_device_ban_device` (`device_id`),
  KEY `idx_device_ban_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备长期封禁';

CREATE TABLE IF NOT EXISTS `sys_user_device_binding` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '用户 ID',
  `device_id` VARCHAR(64) NOT NULL COMMENT '设备 ID',
  `device_name` VARCHAR(100) DEFAULT NULL,
  `approved_by` BIGINT DEFAULT NULL,
  `approved_at` DATETIME DEFAULT NULL,
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_device_binding` (`user_id`, `device_id`),
  KEY `idx_user_device_binding_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户已批准设备（强绑定白名单）';

ALTER TABLE `sys_user`
  ADD COLUMN `device_binding_enabled` TINYINT NOT NULL DEFAULT 0
    COMMENT '是否强制设备绑定（仅白名单设备可登录）' AFTER `dept_id`;

INSERT IGNORE INTO `sys_permission`
(`id`,`permission_code`,`permission_name`,`resource_type`,`resource_path`,`description`,`status`) VALUES
(2182,'admin:device:ban','封禁设备','button',NULL,'设备长期封禁',1),
(2183,'admin:device:unban','解封设备','button',NULL,'解除设备封禁',1),
(2184,'admin:user:device-binding','设备强绑定开关','button',NULL,'启用/关闭用户强制设备绑定',1),
(2185,'admin:user:device-approve','批准登录设备','button',NULL,'批准或撤销用户设备',1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`,`permission_id`,`create_by`) VALUES
(1001,2182,NULL),
(1001,2183,NULL),
(1001,2184,NULL),
(1001,2185,NULL),
(1005,2182,NULL),
(1005,2183,NULL),
(1005,2184,NULL),
(1005,2185,NULL);
