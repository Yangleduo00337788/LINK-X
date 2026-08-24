-- 短视频评论图片

ALTER TABLE `short_video_comment`
  ADD COLUMN `image_key` varchar(512) NULL COMMENT '评论图片对象键' AFTER `mentions`,
  ADD COLUMN `image_storage_provider` varchar(16) NULL COMMENT '图片存储后端' AFTER `image_key`;
