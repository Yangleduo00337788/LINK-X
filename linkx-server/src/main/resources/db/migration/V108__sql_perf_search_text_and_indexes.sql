-- 加密场景搜索索引、用户昵称全文、活跃用户统计索引

-- im_message.search_text：写入时从明文摘要，供 FULLTEXT 检索（content 加密后无法在 SQL 中 LIKE）
SET @col_im_search := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'im_message'
      AND COLUMN_NAME = 'search_text'
);
SET @sql_im_col := IF(
    @col_im_search = 0,
    'ALTER TABLE `im_message` ADD COLUMN `search_text` varchar(2000) DEFAULT NULL COMMENT ''搜索摘要(明文,仅检索)'' AFTER `file_name`',
    'SELECT 1'
);
PREPARE stmt_im_col FROM @sql_im_col;
EXECUTE stmt_im_col;
DEALLOCATE PREPARE stmt_im_col;

SET @idx_im_search_ft := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'im_message'
      AND INDEX_NAME = 'ft_im_message_search_text'
);
SET @sql_im_ft := IF(
    @idx_im_search_ft = 0,
    'ALTER TABLE `im_message` ADD FULLTEXT INDEX `ft_im_message_search_text` (`search_text`) WITH PARSER ngram',
    'SELECT 1'
);
PREPARE stmt_im_ft FROM @sql_im_ft;
EXECUTE stmt_im_ft;
DEALLOCATE PREPARE stmt_im_ft;

-- 明文历史消息回填 search_text
UPDATE `im_message`
SET `search_text` = LEFT(CONCAT_WS(' ', IFNULL(`content`, ''), IFNULL(`file_name`, '')), 2000)
WHERE `search_text` IS NULL
  AND `deleted` = 0
  AND (`content_enc_version` IS NULL OR `content_enc_version` = 0)
  AND (IFNULL(`content`, '') <> '' OR IFNULL(`file_name`, '') <> '');

-- moments_post.search_text
SET @col_mp_search := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'moments_post'
      AND COLUMN_NAME = 'search_text'
);
SET @sql_mp_col := IF(
    @col_mp_search = 0,
    'ALTER TABLE `moments_post` ADD COLUMN `search_text` varchar(2000) DEFAULT NULL COMMENT ''搜索摘要(明文,仅检索)'' AFTER `location`',
    'SELECT 1'
);
PREPARE stmt_mp_col FROM @sql_mp_col;
EXECUTE stmt_mp_col;
DEALLOCATE PREPARE stmt_mp_col;

SET @idx_mp_search_ft := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'moments_post'
      AND INDEX_NAME = 'ft_moments_post_search_text'
);
SET @sql_mp_ft := IF(
    @idx_mp_search_ft = 0,
    'ALTER TABLE `moments_post` ADD FULLTEXT INDEX `ft_moments_post_search_text` (`search_text`) WITH PARSER ngram',
    'SELECT 1'
);
PREPARE stmt_mp_ft FROM @sql_mp_ft;
EXECUTE stmt_mp_ft;
DEALLOCATE PREPARE stmt_mp_ft;

UPDATE `moments_post`
SET `search_text` = LEFT(CONCAT_WS(' ', IFNULL(`content`, ''), IFNULL(`location`, '')), 2000)
WHERE `search_text` IS NULL
  AND `deleted` = 0
  AND (`content_enc_version` IS NULL OR `content_enc_version` = 0)
  AND (IFNULL(`content`, '') <> '' OR IFNULL(`location`, '') <> '');

-- 用户昵称全文检索
SET @idx_user_nickname_ft := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_user'
      AND INDEX_NAME = 'ft_sys_user_nickname'
);
SET @sql_user_ft := IF(
    @idx_user_nickname_ft = 0,
    'ALTER TABLE `sys_user` ADD FULLTEXT INDEX `ft_sys_user_nickname` (`nickname`) WITH PARSER ngram',
    'SELECT 1'
);
PREPARE stmt_user_ft FROM @sql_user_ft;
EXECUTE stmt_user_ft;
DEALLOCATE PREPARE stmt_user_ft;

-- 活跃用户统计
SET @idx_user_active := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'sys_user'
      AND INDEX_NAME = 'idx_sys_user_active'
);
SET @sql_user_active := IF(
    @idx_user_active = 0,
    'CREATE INDEX `idx_sys_user_active` ON `sys_user` (`deleted`, `status`, `update_time`)',
    'SELECT 1'
);
PREPARE stmt_user_active FROM @sql_user_active;
EXECUTE stmt_user_active;
DEALLOCATE PREPARE stmt_user_active;
