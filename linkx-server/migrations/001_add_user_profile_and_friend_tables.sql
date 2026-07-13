-- 升级已有 linkx 数据库到当前版本
-- 适用场景：sys_user 表缺少资料字段，或好友相关表未创建
-- 可重复执行：已存在的列/表会跳过（MySQL 8.0+）

USE `linkx`;

-- sys_user：补充用户资料字段
SET @db = DATABASE();

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'gender') = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `gender` VARCHAR(8) DEFAULT NULL COMMENT ''性别(男/女)'' AFTER `signature`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'birthday') = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `birthday` BIGINT DEFAULT NULL COMMENT ''生日(毫秒时间戳)'' AFTER `gender`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'country') = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `country` VARCHAR(64) DEFAULT NULL COMMENT ''国家'' AFTER `birthday`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'province') = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `province` VARCHAR(64) DEFAULT NULL COMMENT ''省份'' AFTER `country`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'region') = 0,
  'ALTER TABLE `sys_user` ADD COLUMN `region` VARCHAR(64) DEFAULT NULL COMMENT ''地区'' AFTER `province`',
  'SELECT 1'
);
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 好友关系表（若不存在则创建）
CREATE TABLE IF NOT EXISTS `sys_user_relation` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `friend_id` BIGINT NOT NULL COMMENT '好友ID',
  `remark` VARCHAR(64) DEFAULT NULL COMMENT '好友备注',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(1:正常 2:拉黑)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(成为好友的时间)',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:未删除 1:已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_friend` (`user_id`, `friend_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_friend_id` (`friend_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户好友关系表';

-- 好友申请表（若不存在则创建）
CREATE TABLE IF NOT EXISTS `sys_friend_request` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  `from_user_id` BIGINT NOT NULL COMMENT '申请人用户ID',
  `to_user_id` BIGINT NOT NULL COMMENT '被申请人用户ID',
  `message` VARCHAR(255) DEFAULT NULL COMMENT '验证信息',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态(0:待处理 1:已同意 2:已拒绝)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:未删除 1:已删除)',
  PRIMARY KEY (`id`),
  KEY `idx_to_user_status` (`to_user_id`, `status`),
  KEY `idx_from_user` (`from_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友申请表';
