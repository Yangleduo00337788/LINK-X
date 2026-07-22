-- 收藏空间默认配额改为 20GiB（与网盘一致）
ALTER TABLE `favorite_storage`
  MODIFY COLUMN `quota_bytes` bigint NOT NULL DEFAULT 21474836480 COMMENT '配额，默认20GiB';
