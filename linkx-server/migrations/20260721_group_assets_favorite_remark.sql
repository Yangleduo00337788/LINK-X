-- 群共享资源 / 收藏拆分 / 群备注

-- 1. 群成员备注（用户对群的备注，多端同步）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'im_conversation_member' AND COLUMN_NAME = 'remark') = 0,
    'ALTER TABLE `im_conversation_member` ADD COLUMN `remark` varchar(64) DEFAULT NULL COMMENT ''用户对本群备注'' AFTER `role`',
    'SELECT 1'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 群共享资源（文件 / 相册 / 精华）
CREATE TABLE IF NOT EXISTS `group_asset` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `conversation_id` bigint NOT NULL COMMENT '群会话ID',
  `uploader_id` bigint NOT NULL COMMENT '上传者用户ID',
  `type` varchar(20) NOT NULL COMMENT '类型: file / image / essence',
  `title` varchar(255) DEFAULT NULL COMMENT '标题或文件名展示',
  `content` text COMMENT '文本内容或链接（精华）',
  `file_name` varchar(255) DEFAULT NULL COMMENT '原始文件名',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小(字节)',
  `file_key` varchar(500) DEFAULT NULL COMMENT '对象存储 key',
  `message_id` bigint DEFAULT NULL COMMENT '关联消息ID（精华可选）',
  `download_count` int NOT NULL DEFAULT 0 COMMENT '下载次数',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_conv_type_time` (`conversation_id`, `type`, `create_time`),
  KEY `idx_uploader` (`uploader_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群共享资源表';

-- 3. 收藏表（与笔记拆分）
CREATE TABLE IF NOT EXISTS `favorite` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `title` varchar(200) DEFAULT NULL COMMENT '标题',
  `content` text NOT NULL COMMENT '内容/链接/预览',
  `type` varchar(20) NOT NULL DEFAULT 'note' COMMENT '类型: note/image/link/file/message',
  `source_type` varchar(32) DEFAULT NULL COMMENT '来源类型: chat/moments/manual',
  `source_id` varchar(64) DEFAULT NULL COMMENT '来源业务ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `update_time`),
  KEY `idx_user_type` (`user_id`, `type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏表';
