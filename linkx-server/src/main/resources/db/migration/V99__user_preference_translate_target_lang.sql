-- AI 翻译默认目标语言（auto / zh / en / ja / ko）
SET @exist := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'user_preference'
      AND COLUMN_NAME = 'translate_target_lang'
);
SET @sqlstmt := IF(
    @exist = 0,
    'ALTER TABLE `user_preference`
        ADD COLUMN `translate_target_lang` varchar(16) NOT NULL DEFAULT ''auto''
            COMMENT ''AI 翻译目标语言：auto/zh/en/ja/ko'' AFTER `language`',
    'SELECT 1'
);
PREPARE stmt FROM @sqlstmt;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
