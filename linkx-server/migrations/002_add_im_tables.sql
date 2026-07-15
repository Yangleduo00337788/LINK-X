-- 002: 即时消息模块表（会话、成员、消息）
-- 可重复执行：已存在的表会跳过

USE `linkx`;

CREATE TABLE IF NOT EXISTS `im_conversation` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  `type` TINYINT NOT NULL DEFAULT 1 COMMENT '类型(1:单聊 2:群聊)',
  `private_key` VARCHAR(64) DEFAULT NULL COMMENT '单聊唯一键 minUserId_maxUserId',
  `name` VARCHAR(100) DEFAULT NULL COMMENT '群名称（群聊用）',
  `avatar` VARCHAR(500) DEFAULT NULL COMMENT '群头像URL',
  `announcement` TEXT DEFAULT NULL COMMENT '群公告',
  `owner_id` BIGINT DEFAULT NULL COMMENT '群主ID（群聊用）',
  `last_message_content` VARCHAR(500) DEFAULT NULL COMMENT '最后一条消息预览',
  `last_message_time` DATETIME DEFAULT NULL COMMENT '最后一条消息时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:未删除 1:已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_private_key` (`private_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM会话表';

CREATE TABLE IF NOT EXISTS `im_conversation_member` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  `conversation_id` BIGINT NOT NULL COMMENT '会话ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `role` VARCHAR(20) DEFAULT NULL COMMENT '角色(owner/admin/member)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:未删除 1:已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conv_user` (`conversation_id`, `user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM会话成员表';

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
