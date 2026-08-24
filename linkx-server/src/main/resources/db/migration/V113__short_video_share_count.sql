-- 短视频分享次数

ALTER TABLE `short_video_post`
  ADD COLUMN `share_count` bigint NOT NULL DEFAULT 0 COMMENT '分享次数' AFTER `play_count`;
