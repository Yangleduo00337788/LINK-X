-- 消息/朋友圈加密补全任务：按 enc_version 过滤
SET @idx_im_content_enc := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'im_message'
      AND INDEX_NAME = 'idx_im_message_content_enc'
);
SET @sql_im_content := IF(
    @idx_im_content_enc = 0,
    'CREATE INDEX `idx_im_message_content_enc` ON `im_message` (`content_enc_version`, `id`)',
    'SELECT 1'
);
PREPARE stmt_im_content FROM @sql_im_content;
EXECUTE stmt_im_content;
DEALLOCATE PREPARE stmt_im_content;

SET @idx_im_quote_enc := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'im_message'
      AND INDEX_NAME = 'idx_im_message_quote_enc'
);
SET @sql_im_quote := IF(
    @idx_im_quote_enc = 0,
    'CREATE INDEX `idx_im_message_quote_enc` ON `im_message` (`quote_content_enc_version`, `id`)',
    'SELECT 1'
);
PREPARE stmt_im_quote FROM @sql_im_quote;
EXECUTE stmt_im_quote;
DEALLOCATE PREPARE stmt_im_quote;

SET @idx_mp_content_enc := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'moments_post'
      AND INDEX_NAME = 'idx_moments_post_content_enc'
);
SET @sql_mp := IF(
    @idx_mp_content_enc = 0,
    'CREATE INDEX `idx_moments_post_content_enc` ON `moments_post` (`content_enc_version`, `id`)',
    'SELECT 1'
);
PREPARE stmt_mp FROM @sql_mp;
EXECUTE stmt_mp;
DEALLOCATE PREPARE stmt_mp;

SET @idx_mc_content_enc := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'moments_comment'
      AND INDEX_NAME = 'idx_moments_comment_content_enc'
);
SET @sql_mc := IF(
    @idx_mc_content_enc = 0,
    'CREATE INDEX `idx_moments_comment_content_enc` ON `moments_comment` (`content_enc_version`, `id`)',
    'SELECT 1'
);
PREPARE stmt_mc FROM @sql_mc;
EXECUTE stmt_mc;
DEALLOCATE PREPARE stmt_mc;

-- 网盘子树统计：按 user_id + path 前缀定位
SET @idx_cf_user_path := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'cloud_folder'
      AND INDEX_NAME = 'idx_cf_user_path'
);
SET @sql_cf := IF(
    @idx_cf_user_path = 0,
    'CREATE INDEX `idx_cf_user_path` ON `cloud_folder` (`user_id`, `path`(191))',
    'SELECT 1'
);
PREPARE stmt_cf FROM @sql_cf;
EXECUTE stmt_cf;
DEALLOCATE PREPARE stmt_cf;
