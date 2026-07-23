-- ================================================
-- LinkX 数据库初始化脚本
-- 基于当前数据库结构生成
-- ================================================

CREATE DATABASE IF NOT EXISTS `linkx` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `linkx`;

-- ================================================
-- 1. 系统用户表
-- ================================================
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `username` varchar(64) NOT NULL COMMENT '登录账号(LinkX ID)',
  `password` varchar(255) NOT NULL COMMENT '密码(BCrypt加密)',
  `nickname` varchar(64) NOT NULL COMMENT '用户昵称',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
  `signature` varchar(255) DEFAULT NULL COMMENT '个性签名',
  `gender` varchar(8) DEFAULT NULL COMMENT '性别(男/女)',
  `birthday` bigint DEFAULT NULL COMMENT '生日(毫秒时间戳)',
  `country` varchar(64) DEFAULT NULL COMMENT '国家',
  `province` varchar(64) DEFAULT NULL COMMENT '省份',
  `region` varchar(64) DEFAULT NULL COMMENT '地区',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态(1:正常 0:停用)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `create_by` bigint DEFAULT NULL COMMENT '创建人',
  `update_by` bigint DEFAULT NULL COMMENT '更新人',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:未删除 1:已删除)',
  `email` varchar(128) DEFAULT NULL COMMENT '用户邮箱，用于找回密码',
  `phone` varchar(32) DEFAULT NULL COMMENT '手机号，用于账号安全绑定',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- ================================================
-- 2. 好友关系表
-- ================================================
CREATE TABLE IF NOT EXISTS `sys_user_relation` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `friend_id` bigint NOT NULL COMMENT '好友ID',
  `remark` varchar(64) DEFAULT NULL COMMENT '好友备注',
  `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态(1:正常 2:拉黑)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(成为好友的时间)',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:未删除 1:已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_friend` (`user_id`,`friend_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_friend_id` (`friend_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户好友关系表';

-- ================================================
-- 3. 好友申请表
-- ================================================
CREATE TABLE IF NOT EXISTS `sys_friend_request` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `from_user_id` bigint NOT NULL COMMENT '申请人用户ID',
  `to_user_id` bigint NOT NULL COMMENT '被申请人用户ID',
  `message` varchar(255) DEFAULT NULL COMMENT '验证信息',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态(0:待处理 1:已同意 2:已拒绝)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:未删除 1:已删除)',
  PRIMARY KEY (`id`),
  KEY `idx_to_user_status` (`to_user_id`,`status`),
  KEY `idx_from_user` (`from_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='好友申请表';

-- ================================================
-- 4. 登录审计表
-- ================================================
CREATE TABLE IF NOT EXISTS `sys_login_audit` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `username` varchar(64) NOT NULL COMMENT '登录账号',
  `ip` varchar(64) DEFAULT NULL COMMENT '客户端IP',
  `user_agent` varchar(512) DEFAULT NULL COMMENT 'User-Agent',
  `success` tinyint NOT NULL DEFAULT 0 COMMENT '是否成功(1:成功 0:失败)',
  `reason` varchar(255) DEFAULT NULL COMMENT '结果说明',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
  PRIMARY KEY (`id`),
  KEY `idx_username` (`username`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录审计日志';

-- ================================================
-- 4.1 敏感操作审计日志表
-- ================================================
CREATE TABLE IF NOT EXISTS `sys_audit_log` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `operation_type` varchar(50) NOT NULL COMMENT '操作类型: LOGIN/LOGOUT/REGISTER/RESET_PASSWORD等',
  `description` varchar(255) DEFAULT NULL COMMENT '操作描述',
  `user_id` bigint DEFAULT NULL COMMENT '操作者用户ID',
  `username` varchar(64) DEFAULT NULL COMMENT '操作者用户名',
  `target_user_id` bigint DEFAULT NULL COMMENT '目标用户ID',
  `target_username` varchar(64) DEFAULT NULL COMMENT '目标用户名',
  `target_resource_id` varchar(128) DEFAULT NULL COMMENT '目标资源ID',
  `target_resource_type` varchar(50) DEFAULT NULL COMMENT '目标资源类型',
  `ip` varchar(64) DEFAULT NULL COMMENT '客户端IP',
  `user_agent` varchar(512) DEFAULT NULL COMMENT 'User-Agent',
  `status` varchar(20) NOT NULL COMMENT '操作状态: SUCCESS/FAIL',
  `failure_reason` varchar(255) DEFAULT NULL COMMENT '失败原因',
  `extra_data` text COMMENT '额外数据(JSON格式)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_operation_type` (`operation_type`),
  KEY `idx_target_user` (`target_user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='敏感操作审计日志';

-- ================================================
-- 5. 设备会话表
-- ================================================
CREATE TABLE IF NOT EXISTS `sys_device_session` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `device_id` varchar(64) NOT NULL COMMENT '设备ID(唯一标识)',
  `device_name` varchar(100) DEFAULT NULL COMMENT '设备名称',
  `device_type` varchar(20) DEFAULT 'Web' COMMENT '设备类型: Web, Android, iOS, Windows, Mac, Linux',
  `ip` varchar(50) DEFAULT NULL COMMENT 'IP地址',
  `user_agent` varchar(500) DEFAULT NULL COMMENT '浏览器/客户端User-Agent',
  `last_active` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_device` (`user_id`,`device_id`),
  KEY `idx_device_id` (`device_id`),
  KEY `idx_last_active` (`last_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备会话表';

-- ================================================
-- 6. 用户反馈表
-- ================================================
CREATE TABLE IF NOT EXISTS `sys_feedback` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `type` varchar(20) NOT NULL COMMENT '反馈类型: bug(缺陷反馈), suggestion(功能建议), other(其他)',
  `content` text NOT NULL COMMENT '反馈内容',
  `contact` varchar(100) DEFAULT NULL COMMENT '联系方式',
  `status` varchar(20) NOT NULL DEFAULT 'pending' COMMENT '状态: pending(待处理), processed(已处理), closed(已关闭)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`create_time`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户反馈表';

-- ================================================
-- 7. IM 会话表
-- ================================================
CREATE TABLE IF NOT EXISTS `im_conversation` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `type` tinyint NOT NULL DEFAULT 1 COMMENT '类型(1:单聊 2:群聊)',
  `private_key` varchar(64) DEFAULT NULL COMMENT '单聊唯一键 minUserId_maxUserId',
  `name` varchar(100) DEFAULT NULL COMMENT '群名称(群聊可用)',
  `avatar` varchar(500) DEFAULT NULL COMMENT '群头像URL',
  `announcement` text COMMENT '群公告',
  `owner_id` bigint DEFAULT NULL COMMENT '群主ID(群聊可用)',
  `mute_all` tinyint(1) NOT NULL DEFAULT 0 COMMENT '全体禁言(0关1开)',
  `mute_all_start` datetime DEFAULT NULL COMMENT '定时全体禁言开始时间',
  `mute_all_end` datetime DEFAULT NULL COMMENT '定时全体禁言结束时间',
  `last_message_content` varchar(500) DEFAULT NULL COMMENT '最后一条消息预览',
  `last_message_time` datetime DEFAULT NULL COMMENT '最后一条消息时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:未删除 1:已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_private_key` (`private_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM会话表';

-- ================================================
-- 8. IM 会话成员表
-- ================================================
CREATE TABLE IF NOT EXISTS `im_conversation_member` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `conversation_id` bigint NOT NULL COMMENT '会话ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role` varchar(20) DEFAULT NULL COMMENT '角色(owner/admin/member)',
  `remark` varchar(64) DEFAULT NULL COMMENT '用户对本群备注',
  `muted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否禁言(0否1是)',
  `mute_until` datetime DEFAULT NULL COMMENT '成员禁言截止时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:未删除 1:已删除)',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conv_user` (`conversation_id`,`user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM会话成员表';

-- ================================================
-- 9. IM 消息表
-- ================================================
CREATE TABLE IF NOT EXISTS `im_message` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `conversation_id` bigint NOT NULL COMMENT '会话ID',
  `sender_id` bigint NOT NULL COMMENT '发送者用户ID',
  `type` varchar(20) NOT NULL DEFAULT 'text' COMMENT '消息类型(text/image/file)',
  `content` text COMMENT '文本内容或预览',
  `file_name` varchar(255) DEFAULT NULL COMMENT '文件名',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小(字节)',
  `file_url` varchar(500) DEFAULT NULL COMMENT '文件/图片URL',
  `voice_duration` int DEFAULT NULL COMMENT '语音时长(秒)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除(0:未删除 1:已删除)',
  PRIMARY KEY (`id`),
  KEY `idx_conv_time` (`conversation_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='IM消息表';

-- ================================================
-- 10. 朋友圈动态表
-- ================================================
CREATE TABLE IF NOT EXISTS `moments_post` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '发布者用户ID',
  `content` text COMMENT '动态内容',
  `location` varchar(255) DEFAULT NULL COMMENT '位置文案',
  `at_users` text COMMENT '提及用户 JSON',
  `visibility` int DEFAULT 0 COMMENT '可见范围',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='朋友圈动态表';

-- ================================================
-- 11. 朋友圈图片表
-- ================================================
CREATE TABLE IF NOT EXISTS `moments_image` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `post_id` bigint NOT NULL COMMENT '动态ID',
  `url` varchar(500) NOT NULL COMMENT '图片URL',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序顺序',
  PRIMARY KEY (`id`),
  KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='朋友圈图片表';

-- ================================================
-- 12. 朋友圈点赞表
-- ================================================
CREATE TABLE IF NOT EXISTS `moments_like` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `post_id` bigint NOT NULL COMMENT '动态ID',
  `user_id` bigint NOT NULL COMMENT '点赞用户ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_user` (`post_id`,`user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='朋友圈点赞表';

-- ================================================
-- 13. 朋友圈评论表
-- ================================================
CREATE TABLE IF NOT EXISTS `moments_comment` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `post_id` bigint NOT NULL COMMENT '动态ID',
  `user_id` bigint NOT NULL COMMENT '评论用户ID',
  `content` varchar(500) NOT NULL COMMENT '评论内容',
  `parent_id` bigint DEFAULT NULL COMMENT '父评论ID(回复功能)',
  `mentions` text DEFAULT NULL COMMENT '被@的用户ID列表(JSON 数组)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_post_time` (`post_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='朋友圈评论表';

-- 兼容老库:为已存在的 moments_comment 增加 mentions 列(幂等)
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'moments_comment' AND COLUMN_NAME = 'mentions') = 0,
    'ALTER TABLE `moments_comment` ADD COLUMN `mentions` TEXT DEFAULT NULL COMMENT ''被@的用户ID列表(JSON 数组)''',
    'SELECT 1'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ================================================
-- 14. 笔记表
-- ================================================
CREATE TABLE IF NOT EXISTS `note` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `title` varchar(200) DEFAULT NULL COMMENT '笔记标题',
  `content` text COMMENT '笔记内容(Markdown)',
  `type` varchar(20) NOT NULL DEFAULT 'note' COMMENT '笔记/收藏类型: note/image/link/file',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`update_time`),
  KEY `idx_user_type` (`user_id`,`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='笔记表';

-- ================================================
-- 15. 用户余额表
-- ================================================
CREATE TABLE IF NOT EXISTS `user_balance` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `balance` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '余额(元)',
  `frozen` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '冻结金额(预扣款)',
  `total_recharge` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计充值金额',
  `total_withdraw` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计支出金额',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户余额表';

-- ================================================
-- 16. 余额变动记录表
-- ================================================
CREATE TABLE IF NOT EXISTS `balance_log` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `type` varchar(20) NOT NULL COMMENT '类型: recharge(充值), send_redpacket(发红包), receive_redpacket(收红包), refund(退款)',
  `amount` decimal(12,2) NOT NULL COMMENT '变动金额(正数)',
  `balance_before` decimal(12,2) NOT NULL COMMENT '变动前余额',
  `balance_after` decimal(12,2) NOT NULL COMMENT '变动后余额',
  `biz_type` varchar(50) DEFAULT NULL COMMENT '业务类型: red_packet(红包), withdrawal(提现)',
  `biz_id` varchar(64) DEFAULT NULL COMMENT '业务ID',
  `remark` varchar(200) DEFAULT NULL COMMENT '备注',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人ID(管理员操作时)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='余额变动记录表';

-- ================================================
-- 17. 红包表
-- ================================================
CREATE TABLE IF NOT EXISTS `red_packet` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `sender_id` bigint NOT NULL COMMENT '发送者ID',
  `conversation_id` bigint NOT NULL COMMENT '会话ID(群聊发送)',
  `type` varchar(20) NOT NULL COMMENT '红包类型: normal(普通红包), lucky(拼手气红包)',
  `total_amount` decimal(12,2) NOT NULL COMMENT '总金额',
  `total_count` int NOT NULL COMMENT '红包个数',
  `remaining_amount` decimal(12,2) NOT NULL COMMENT '剩余金额',
  `remaining_count` int NOT NULL COMMENT '剩余个数',
  `greeting` varchar(200) DEFAULT '恭喜发财' COMMENT '祝福语',
  `status` varchar(20) NOT NULL DEFAULT 'active' COMMENT '状态: active(有效), expired(过期), finished(领完)',
  `expire_time` datetime NOT NULL COMMENT '过期时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `version` bigint NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  PRIMARY KEY (`id`),
  KEY `idx_conversation_time` (`conversation_id`,`create_time`),
  KEY `idx_sender_time` (`sender_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='红包表';

-- ================================================
-- 18. 红包领取记录表
-- ================================================
CREATE TABLE IF NOT EXISTS `red_packet_record` (
  `id` bigint NOT NULL COMMENT '主键ID',
  `red_packet_id` bigint NOT NULL COMMENT '红包ID',
  `user_id` bigint NOT NULL COMMENT '领取人ID',
  `amount` decimal(12,2) NOT NULL COMMENT '领取金额',
  `is_lucky` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否手气最佳',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_red_packet_user` (`red_packet_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='红包领取记录表';

-- ================================================
-- 数据库兼容性补丁：对已存在的表添加缺失列（幂等）
-- ================================================

-- sys_user 表新增 email 列（如不存在）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'email') = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `email` varchar(128) DEFAULT NULL COMMENT ''用户邮箱，用于找回密码''',
    'SELECT 1'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- sys_user 表新增 phone 列（如不存在）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'phone') = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `phone` varchar(32) DEFAULT NULL COMMENT ''手机号，用于账号安全绑定''',
    'SELECT 1'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- note 表新增 type 列（如不存在）
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'note' AND COLUMN_NAME = 'type') = 0,
    'ALTER TABLE `note` ADD COLUMN `type` varchar(20) NOT NULL DEFAULT ''note'' COMMENT ''笔记/收藏类型: note/image/link/file'', ADD KEY `idx_user_type` (`user_id`,`type`)',
    'SELECT 1'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- red_packet 表新增 version 列（如不存在）- 乐观锁必需
SET @sql = (SELECT IF(
    (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'red_packet' AND COLUMN_NAME = 'version') = 0,
    'ALTER TABLE `red_packet` ADD COLUMN `version` bigint NOT NULL DEFAULT 0 COMMENT ''乐观锁版本号''',
    'SELECT 1'
));
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ================================================
-- 19. 日历事件表
-- ================================================
CREATE TABLE IF NOT EXISTS `calendar_event` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法生成)',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `title` varchar(200) NOT NULL COMMENT '日程标题',
  `date` varchar(10) NOT NULL COMMENT '日期 YYYY-MM-DD',
  `time` varchar(5) DEFAULT NULL COMMENT '时间 HH:mm',
  `color` varchar(50) DEFAULT 'var(--lx-accent)' COMMENT '展示颜色',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) DEFAULT 0 COMMENT '逻辑删除标记 0=未删除 1=已删除',
  PRIMARY KEY (`id`),
  KEY `idx_calendar_user_id` (`user_id`),
  KEY `idx_calendar_date` (`date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日历事件表';

-- ================================================
-- 20. 消息通知表
-- ================================================
CREATE TABLE IF NOT EXISTS `message_notification` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法生成)',
  `user_id` bigint NOT NULL COMMENT '接收通知的用户ID',
  `sender_id` bigint DEFAULT NULL COMMENT '发送者用户ID',
  `sender_name` varchar(100) DEFAULT NULL COMMENT '发送者昵称(冗余存储)',
  `sender_avatar` varchar(500) DEFAULT NULL COMMENT '发送者头像(冗余存储)',
  `type` varchar(50) NOT NULL COMMENT '通知类型: moments_like/moments_comment/moments_follow/...',
  `related_id` bigint DEFAULT NULL COMMENT '关联ID(如朋友圈动态ID)',
  `content` varchar(500) DEFAULT NULL COMMENT '通知内容摘要',
  `read_status` tinyint(1) DEFAULT 0 COMMENT '已读状态 0=未读 1=已读',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` tinyint(1) DEFAULT 0 COMMENT '逻辑删除标记 0=未删除 1=已删除',
  PRIMARY KEY (`id`),
  KEY `idx_notification_user_id` (`user_id`),
  KEY `idx_notification_user_unread` (`user_id`,`read_status`),
  KEY `idx_notification_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息通知表';

-- ================================================
-- 21. 用户偏好设置表（per-user 持久化）
-- ================================================
CREATE TABLE IF NOT EXISTS `user_preference` (
  `user_id` bigint NOT NULL COMMENT '用户ID（主键，与 sys_user.id 一一对应）',
  `auto_start` tinyint(1) NOT NULL DEFAULT 0 COMMENT '开机自动启动',
  `sound_notify` tinyint(1) NOT NULL DEFAULT 1 COMMENT '新消息声音提示',
  `message_detail` tinyint(1) NOT NULL DEFAULT 1 COMMENT '通知显示消息详情',
  `notify_at_me` tinyint(1) NOT NULL DEFAULT 1 COMMENT '群聊@我特别提醒',
  `notify_sound` tinyint(1) NOT NULL DEFAULT 0 COMMENT '通知提示音',
  `privacy_verify_friend` tinyint(1) NOT NULL DEFAULT 1 COMMENT '加好友需验证',
  `privacy_allow_stranger` tinyint(1) NOT NULL DEFAULT 0 COMMENT '允许陌生人会话',
  `privacy_show_online` tinyint(1) NOT NULL DEFAULT 1 COMMENT '在线状态可见',
  `language` varchar(16) NOT NULL DEFAULT 'zh-CN' COMMENT '界面语言',
  `chat_background` varchar(32) NOT NULL DEFAULT 'default' COMMENT '聊天背景主题',
  `notify_tone` varchar(32) NOT NULL DEFAULT 'default' COMMENT '提示音（音色 ID）',
  `moments_background` varchar(512) DEFAULT NULL COMMENT '友链背景图（对象存储 key）',
  `favorites_view_mode` varchar(16) DEFAULT 'grid' COMMENT '收藏视图: grid/list',
  `favorites_sort` varchar(16) DEFAULT 'newest' COMMENT '收藏排序: newest/oldest/title',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户偏好设置表（一行一用户）';

-- ================================================
-- 22. 群邀请表
-- ================================================
CREATE TABLE IF NOT EXISTS `group_invitation` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `conversation_id` bigint NOT NULL COMMENT '群会话ID',
  `inviter_user_id` bigint NOT NULL COMMENT '邀请人用户ID（群成员）',
  `invitee_user_id` bigint NOT NULL COMMENT '被邀请人用户ID',
  `message` varchar(255) DEFAULT NULL COMMENT '邀请留言',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0=待处理 1=已同意 2=已拒绝 3=已过期',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '邀请时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pending_invitee` (`conversation_id`,`invitee_user_id`,`status`),
  KEY `idx_invitee_status` (`invitee_user_id`,`status`),
  KEY `idx_inviter` (`inviter_user_id`),
  KEY `idx_conversation` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群邀请表';

-- ================================================
-- 23. 群共享资源表
-- ================================================
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

-- ================================================
-- 23b. 群公告表（多条 + 置顶）
-- ================================================
CREATE TABLE IF NOT EXISTS `group_announcement` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `conversation_id` bigint NOT NULL COMMENT '群会话ID',
  `content` text NOT NULL COMMENT '公告内容',
  `publisher_id` bigint NOT NULL COMMENT '发布者用户ID',
  `pinned` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否置顶(0/1)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_conv_pinned_time` (`conversation_id`, `pinned`, `create_time`),
  KEY `idx_publisher` (`publisher_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='群公告表';

-- ================================================
-- 24. 收藏表（与笔记拆分）
-- ================================================
CREATE TABLE IF NOT EXISTS `favorite` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `title` varchar(200) DEFAULT NULL COMMENT '标题',
  `content` text NOT NULL COMMENT '内容/链接/预览',
  `type` varchar(20) NOT NULL DEFAULT 'note' COMMENT '类型: note/image/link/file/message',
  `source_type` varchar(32) DEFAULT NULL COMMENT '来源类型',
  `source_id` varchar(64) DEFAULT NULL COMMENT '来源业务ID',
  `tags` varchar(500) DEFAULT NULL COMMENT 'JSON 字符串数组，如 ["工作","学习"]',
  `file_size` bigint DEFAULT NULL COMMENT '文件/图片大小（字节）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `update_time`),
  KEY `idx_user_type` (`user_id`, `type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏表';

-- ================================================
-- 25. 收藏空间配额
-- ================================================
CREATE TABLE IF NOT EXISTS `favorite_storage` (
  `user_id`      bigint NOT NULL COMMENT '用户ID',
  `quota_bytes`  bigint NOT NULL DEFAULT 21474836480 COMMENT '配额，默认20GiB',
  `used_bytes`   bigint NOT NULL DEFAULT 0 COMMENT '已用（可由 file_size 汇总校正）',
  `item_count`   int    NOT NULL DEFAULT 0 COMMENT '收藏条数',
  `version`      int    NOT NULL DEFAULT 0,
  `create_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏空间配额';

-- ================================================
-- 26. 收藏标签库
-- ================================================
CREATE TABLE IF NOT EXISTS `favorite_tag` (
  `id`           bigint NOT NULL,
  `user_id`      bigint NOT NULL,
  `name`         varchar(64) NOT NULL,
  `color`        varchar(16) DEFAULT NULL COMMENT '展示色，如 #3b82f6',
  `sort_order`   int NOT NULL DEFAULT 0,
  `preset`       tinyint(1) NOT NULL DEFAULT 0 COMMENT '1=系统预设',
  `create_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_ft_user_name` (`user_id`, `name`, `deleted`),
  KEY `idx_ft_user` (`user_id`, `deleted`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户收藏标签库';

-- ================================================
-- 27. 个人网盘：配额 / 文件夹 / 文件 / 标签 / 分享 / 动态
-- ================================================
CREATE TABLE IF NOT EXISTS `user_storage` (
  `user_id`      bigint NOT NULL COMMENT '用户ID',
  `quota_bytes`  bigint NOT NULL DEFAULT 21474836480 COMMENT '配额，默认20GiB',
  `used_bytes`   bigint NOT NULL DEFAULT 0 COMMENT '已用',
  `file_count`   int    NOT NULL DEFAULT 0,
  `version`      int    NOT NULL DEFAULT 0 COMMENT '乐观锁',
  `create_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户网盘配额';

CREATE TABLE IF NOT EXISTS `cloud_folder` (
  `id`           bigint NOT NULL,
  `user_id`      bigint NOT NULL,
  `parent_id`    bigint DEFAULT NULL COMMENT 'NULL=根目录',
  `name`         varchar(255) NOT NULL,
  `path`         varchar(1024) NOT NULL DEFAULT '/' COMMENT '物化路径',
  `sort_order`   int NOT NULL DEFAULT 0,
  `create_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_cf_user_parent` (`user_id`, `parent_id`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人网盘文件夹';

CREATE TABLE IF NOT EXISTS `cloud_file` (
  `id`             bigint NOT NULL,
  `user_id`        bigint NOT NULL,
  `folder_id`      bigint DEFAULT NULL COMMENT 'NULL=根目录',
  `name`           varchar(255) NOT NULL COMMENT '展示名',
  `file_name`      varchar(255) NOT NULL COMMENT '原始文件名',
  `file_size`      bigint NOT NULL DEFAULT 0,
  `file_key`       varchar(500) NOT NULL,
  `content_type`   varchar(128) DEFAULT NULL,
  `ext`            varchar(32) DEFAULT NULL,
  `category`       varchar(20) NOT NULL DEFAULT 'other',
  `description`    varchar(1000) DEFAULT NULL,
  `create_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`        tinyint(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_cfile_user_folder` (`user_id`, `folder_id`, `deleted`, `create_time`),
  KEY `idx_cfile_category` (`user_id`, `category`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人网盘文件';

CREATE TABLE IF NOT EXISTS `cloud_file_tag` (
  `id`          bigint NOT NULL,
  `user_id`     bigint NOT NULL,
  `file_id`     bigint NOT NULL,
  `tag_name`    varchar(64) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cft_file_tag` (`file_id`, `tag_name`),
  KEY `idx_cft_user_tag` (`user_id`, `tag_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网盘文件标签';

CREATE TABLE IF NOT EXISTS `cloud_share` (
  `id`              bigint NOT NULL,
  `user_id`         bigint NOT NULL,
  `share_type`      varchar(16) NOT NULL COMMENT 'file / folder',
  `target_id`       bigint NOT NULL,
  `token`           varchar(64) NOT NULL,
  `password_hash`   varchar(255) DEFAULT NULL,
  `expire_at`       datetime DEFAULT NULL,
  `max_downloads`   int DEFAULT NULL,
  `download_count`  int NOT NULL DEFAULT 0,
  `status`          tinyint NOT NULL DEFAULT 1 COMMENT '1有效 0关闭',
  `create_time`     datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cs_token` (`token`),
  KEY `idx_cs_user_target` (`user_id`, `share_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网盘分享';

CREATE TABLE IF NOT EXISTS `cloud_activity` (
  `id`           bigint NOT NULL,
  `user_id`      bigint NOT NULL,
  `target_type`  varchar(16) NOT NULL COMMENT 'file / folder',
  `target_id`    bigint NOT NULL,
  `target_name`  varchar(255) DEFAULT NULL,
  `action`       varchar(32) NOT NULL COMMENT 'upload/create/rename/move/delete/share/tag/download/expand',
  `detail`       varchar(500) DEFAULT NULL,
  `create_time`  datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ca_user_target` (`user_id`, `target_type`, `target_id`, `create_time`),
  KEY `idx_ca_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网盘动态';

-- ================================================
-- 28. 敏感词表
-- ================================================
CREATE TABLE IF NOT EXISTS `sys_sensitive_word` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `word` varchar(100) NOT NULL COMMENT '敏感词',
  `category` varchar(32) DEFAULT 'general' COMMENT '分类: general/politics/violence/ad',
  `action` varchar(20) NOT NULL DEFAULT 'filter' COMMENT '处理方式: filter(替换) / block(拦截) / alert(警告)',
  `replacement` varchar(10) DEFAULT '***' COMMENT '替换文本(仅filter时生效)',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word` (`word`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='敏感词库';

-- ================================================
-- 29. 用户黑名单表
-- ================================================
CREATE TABLE IF NOT EXISTS `sys_user_blacklist` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `user_id` bigint NOT NULL COMMENT '拉黑者用户ID',
  `blocked_user_id` bigint NOT NULL COMMENT '被拉黑用户ID',
  `reason` varchar(255) DEFAULT NULL COMMENT '拉黑原因',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '拉黑时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_blocked` (`user_id`,`blocked_user_id`),
  KEY `idx_blocked_user` (`blocked_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户黑名单';

-- ================================================
-- 30. 多人会议表
-- ================================================
CREATE TABLE IF NOT EXISTS `conference` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `title` varchar(200) DEFAULT '多人会议' COMMENT '会议标题',
  `type` varchar(10) NOT NULL DEFAULT 'video' COMMENT '会议类型: voice/video',
  `creator_id` bigint NOT NULL COMMENT '创建者用户ID',
  `conversation_id` bigint DEFAULT NULL COMMENT '关联群会话ID(可选)',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态: 0创建中 1进行中 2已结束',
  `max_participants` int NOT NULL DEFAULT 9 COMMENT '最大参与人数',
  `start_time` datetime DEFAULT NULL COMMENT '会议开始时间',
  `end_time` datetime DEFAULT NULL COMMENT '会议结束时间',
  `password` varchar(64) DEFAULT NULL COMMENT '会议密码(可选)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_conf_creator` (`creator_id`),
  KEY `idx_conf_status` (`status`),
  KEY `idx_conf_conv` (`conversation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='多人会议表';

-- ================================================
-- 31. 多人会议成员表
-- ================================================
CREATE TABLE IF NOT EXISTS `conference_member` (
  `id` bigint NOT NULL COMMENT '主键ID(雪花算法)',
  `conference_id` bigint NOT NULL COMMENT '会议ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role` varchar(20) NOT NULL DEFAULT 'member' COMMENT '角色: host/co-host/member',
  `muted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否静音',
  `video_off` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否关闭摄像头',
  `left` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已离开',
  `join_time` datetime DEFAULT NULL COMMENT '加入时间',
  `leave_time` datetime DEFAULT NULL COMMENT '离开时间',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conf_user` (`conference_id`,`user_id`),
  KEY `idx_cm_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='多人会议成员表';

