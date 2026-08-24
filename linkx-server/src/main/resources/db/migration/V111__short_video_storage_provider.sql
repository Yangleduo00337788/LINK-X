-- 短视频按上传时存储后端隔离展示（minio | oss | cos）
ALTER TABLE `short_video_post`
    ADD COLUMN `storage_provider` VARCHAR(16) DEFAULT NULL
        COMMENT 'minio|oss|cos，上传时全局 storage_provider'
        AFTER `video_key`;

CREATE INDEX `idx_short_video_post_storage`
    ON `short_video_post` (`storage_provider`, `deleted`, `create_time`);
