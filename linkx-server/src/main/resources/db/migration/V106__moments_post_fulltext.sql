-- 朋友圈全文检索（ngram，仅在未开启落库加密时使用）
SET @idx_ft_moments := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'moments_post'
      AND INDEX_NAME = 'ft_moments_post_search'
);
SET @sql_ft_moments := IF(
    @idx_ft_moments = 0,
    'ALTER TABLE `moments_post` ADD FULLTEXT INDEX `ft_moments_post_search` (`content`, `location`) WITH PARSER ngram',
    'SELECT 1'
);
PREPARE stmt_ft_moments FROM @sql_ft_moments;
EXECUTE stmt_ft_moments;
DEALLOCATE PREPARE stmt_ft_moments;
