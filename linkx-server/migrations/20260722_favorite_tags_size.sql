-- 收藏：标签与文件大小（用于收藏页卡片展示）
-- 若列已存在可忽略报错后继续

ALTER TABLE `favorite`
  ADD COLUMN `tags` varchar(500) DEFAULT NULL COMMENT 'JSON 字符串数组，如 ["工作","学习"]' AFTER `source_id`;

ALTER TABLE `favorite`
  ADD COLUMN `file_size` bigint DEFAULT NULL COMMENT '文件/图片大小（字节）' AFTER `tags`;
