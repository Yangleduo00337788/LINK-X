-- 创建数据库
CREATE DATABASE IF NOT EXISTS `linkx` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE `linkx`;

-- ==============================================
-- 1. 系统用户表
-- ==============================================
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  `username` VARCHAR(64) NOT NULL COMMENT '登录账号(LinkX ID)',
  `password` VARCHAR(255) NOT NULL COMMENT '密码(BCrypt加密)',
  `nickname` VARCHAR(64) NOT NULL COMMENT '用户昵称',
  `avatar` VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
  `signature` VARCHAR(255) DEFAULT NULL COMMENT '个性签名',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(1:正常 0:停用)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` BIGINT DEFAULT NULL COMMENT '创建人',
  `update_by` BIGINT DEFAULT NULL COMMENT '更新人',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:未删除 1:已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- 插入一条测试数据 (密码为 123456 的 BCrypt Hash)
INSERT IGNORE INTO `sys_user` (`id`, `username`, `password`, `nickname`, `avatar`) 
VALUES (1, 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统管理员', 'https://api.dicebear.com/7.x/adventurer/svg?seed=admin');

-- ==============================================
-- 2. 好友关系表
-- ==============================================
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
