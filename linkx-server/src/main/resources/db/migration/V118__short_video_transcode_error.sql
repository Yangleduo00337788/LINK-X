-- 短视频转码失败原因（管理端展示）

ALTER TABLE `short_video_post`
  ADD COLUMN `transcode_error` varchar(500) DEFAULT NULL COMMENT '转码失败原因' AFTER `transcoded_video_key`;
