-- =============================================================================
-- V54: 反馈多轮回复表（sys_feedback_reply）
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_feedback_reply` (
  `id`           bigint       NOT NULL COMMENT '雪花ID',
  `feedback_id`  bigint       NOT NULL COMMENT '反馈ID',
  `sender_type`  varchar(16)  NOT NULL COMMENT '发送方：admin|user',
  `sender_id`    bigint       NULL COMMENT '发送人用户ID',
  `sender_name`  varchar(64)  NULL COMMENT '发送人展示名',
  `content`      text         NOT NULL COMMENT '回复内容',
  `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_feedback_reply_feedback` (`feedback_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='反馈多轮回复';

-- 历史单条 reply 字段迁移为首条官方回复
INSERT INTO `sys_feedback_reply` (`id`, `feedback_id`, `sender_type`, `sender_id`, `sender_name`, `content`, `create_time`)
SELECT
  (`id` * 10) + 1,
  `id`,
  'admin',
  NULL,
  'LinkX官方',
  `reply`,
  IFNULL(`reply_time`, `create_time`)
FROM `sys_feedback`
WHERE `reply` IS NOT NULL
  AND TRIM(`reply`) <> ''
  AND NOT EXISTS (
    SELECT 1 FROM `sys_feedback_reply` r WHERE r.`feedback_id` = `sys_feedback`.`id`
  );
