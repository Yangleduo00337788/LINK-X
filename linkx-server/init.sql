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
  `gender` VARCHAR(8) DEFAULT NULL COMMENT '性别(男/女)',
  `birthday` BIGINT DEFAULT NULL COMMENT '生日(毫秒时间戳)',
  `country` VARCHAR(64) DEFAULT NULL COMMENT '国家',
  `province` VARCHAR(64) DEFAULT NULL COMMENT '省份',
  `region` VARCHAR(64) DEFAULT NULL COMMENT '地区',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态(1:正常 0:停用)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` BIGINT DEFAULT NULL COMMENT '创建人',
  `update_by` BIGINT DEFAULT NULL COMMENT '更新人',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:未删除 1:已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

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

-- ==============================================
-- 4. 好友申请表
-- ==============================================
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

-- ==============================================
-- 5. IM 会话表
-- ==============================================
CREATE TABLE IF NOT EXISTS `im_conversation` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  `type` TINYINT NOT NULL DEFAULT 1 COMMENT '类型(1:单聊 2:群聊)',
  `private_key` VARCHAR(64) DEFAULT NULL COMMENT '单聊唯一键 minUserId_maxUserId',
  `last_message_content` VARCHAR(500) DEFAULT NULL COMMENT '最后一条消息预览',
  `last_message_time` DATETIME DEFAULT NULL COMMENT '最后一条消息时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:未删除 1:已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_private_key` (`private_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM会话表';

-- ==============================================
-- 6. IM 会话成员表
-- ==============================================
CREATE TABLE IF NOT EXISTS `im_conversation_member` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  `conversation_id` BIGINT NOT NULL COMMENT '会话ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:未删除 1:已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conv_user` (`conversation_id`, `user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM会话成员表';

-- ==============================================
-- 7. IM 消息表
-- ==============================================
CREATE TABLE IF NOT EXISTS `im_message` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  `conversation_id` BIGINT NOT NULL COMMENT '会话ID',
  `sender_id` BIGINT NOT NULL COMMENT '发送者用户ID',
  `type` VARCHAR(20) NOT NULL DEFAULT 'text' COMMENT '消息类型(text/image/file)',
  `content` TEXT COMMENT '文本内容或预览',
  `file_name` VARCHAR(255) DEFAULT NULL COMMENT '文件名',
  `file_size` BIGINT DEFAULT NULL COMMENT '文件大小(字节)',
  `file_url` VARCHAR(500) DEFAULT NULL COMMENT '文件/图片URL',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:未删除 1:已删除)',
  PRIMARY KEY (`id`),
  KEY `idx_conv_time` (`conversation_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM消息表';

-- ==============================================
-- 8. 登录审计表
-- ==============================================
CREATE TABLE IF NOT EXISTS `sys_login_audit` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` BIGINT DEFAULT NULL COMMENT '用户ID',
  `username` VARCHAR(64) NOT NULL COMMENT '登录账号',
  `ip` VARCHAR(64) DEFAULT NULL COMMENT '客户端IP',
  `user_agent` VARCHAR(512) DEFAULT NULL COMMENT 'User-Agent',
  `success` TINYINT NOT NULL DEFAULT 0 COMMENT '是否成功(1:成功 0:失败)',
  `reason` VARCHAR(255) DEFAULT NULL COMMENT '结果说明',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (`id`),
  KEY `idx_username` (`username`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录审计日志';
