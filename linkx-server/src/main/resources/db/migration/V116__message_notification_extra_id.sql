-- 消息通知扩展 ID（如短视频评论通知存 commentId）

ALTER TABLE `message_notification`
  ADD COLUMN `extra_id` bigint NULL COMMENT '扩展关联 ID' AFTER `related_id`;
