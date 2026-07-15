-- 余额和红包模块表

USE `linkx`;

-- 用户余额表
CREATE TABLE IF NOT EXISTS `user_balance` (
  `id` BIGINT NOT NULL COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `balance` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '余额(元)',
  `frozen` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '冻结金额(红包预扣)',
  `total_recharge` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计充值金额',
  `total_withdraw` DECIMAL(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计支出金额',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户余额表';

-- 余额变动记录表
CREATE TABLE IF NOT EXISTS `balance_log` (
  `id` BIGINT NOT NULL COMMENT '主键ID',
  `user_id` BIGINT NOT NULL COMMENT '用户ID',
  `type` VARCHAR(20) NOT NULL COMMENT '类型: recharge(充值), send_redpacket(发红包), receive_redpacket(收红包), refund(退款)',
  `amount` DECIMAL(12,2) NOT NULL COMMENT '变动金额(正负)',
  `balance_before` DECIMAL(12,2) NOT NULL COMMENT '变动前余额',
  `balance_after` DECIMAL(12,2) NOT NULL COMMENT '变动后余额',
  `biz_type` VARCHAR(50) DEFAULT NULL COMMENT '业务类型: red_packet(红包), withdrawal(提现)',
  `biz_id` VARCHAR(64) DEFAULT NULL COMMENT '业务ID',
  `remark` VARCHAR(200) DEFAULT NULL COMMENT '备注',
  `operator_id` BIGINT DEFAULT NULL COMMENT '操作人ID(管理员充值时)',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='余额变动记录表';

-- 红包表
CREATE TABLE IF NOT EXISTS `red_packet` (
  `id` BIGINT NOT NULL COMMENT '主键ID',
  `sender_id` BIGINT NOT NULL COMMENT '发送者ID',
  `conversation_id` BIGINT NOT NULL COMMENT '会话ID(群或单聊)',
  `type` VARCHAR(20) NOT NULL COMMENT '红包类型: normal(普通红包), lucky(拼手气红包)',
  `total_amount` DECIMAL(12,2) NOT NULL COMMENT '总金额',
  `total_count` INT NOT NULL COMMENT '红包个数',
  `remaining_amount` DECIMAL(12,2) NOT NULL COMMENT '剩余金额',
  `remaining_count` INT NOT NULL COMMENT '剩余个数',
  `greeting` VARCHAR(200) DEFAULT '恭喜发财' COMMENT '祝福语',
  `status` VARCHAR(20) NOT NULL DEFAULT 'active' COMMENT '状态: active(有效), expired(过期), finished(领完)',
  `expire_time` DATETIME NOT NULL COMMENT '过期时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_conversation_time` (`conversation_id`, `create_time`),
  KEY `idx_sender_time` (`sender_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='红包表';

-- 红包领取记录表
CREATE TABLE IF NOT EXISTS `red_packet_record` (
  `id` BIGINT NOT NULL COMMENT '主键ID',
  `red_packet_id` BIGINT NOT NULL COMMENT '红包ID',
  `user_id` BIGINT NOT NULL COMMENT '领取者ID',
  `amount` DECIMAL(12,2) NOT NULL COMMENT '领取金额',
  `is_lucky` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否是手气最佳',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_packet_user` (`red_packet_id`, `user_id`),
  KEY `idx_user_time` (`user_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='红包领取记录表';
