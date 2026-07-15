-- 008: 红包唯一约束 + 索引优化（防止并发重复领取）

USE `linkx`;

-- ============================================
-- 红包领取记录表添加唯一约束
-- ============================================
-- 注意：如果之前已存在重复领取记录，需要先清理
DROP PROCEDURE IF EXISTS add_unique_index_if_not_exists;

DELIMITER //
CREATE PROCEDURE add_unique_index_if_not_exists(
  IN p_table VARCHAR(128),
  IN p_index VARCHAR(128),
  IN p_columns VARCHAR(255)
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table
      AND INDEX_NAME = p_index
  ) THEN
    -- 先清理重复记录，每个红包只保留最早的一条
    SET @clean_sql = CONCAT(
      'DELETE r1 FROM `', p_table, '` r1 ',
      'INNER JOIN `', p_table, '` r2 ',
      'WHERE r1.red_packet_id = r2.red_packet_id ',
      'AND r1.user_id = r2.user_id ',
      'AND r1.id > r2.id'
    );
    PREPARE clean_stmt FROM @clean_sql;
    EXECUTE clean_stmt;
    DEALLOCATE PREPARE clean_stmt;

    -- 创建唯一索引
    SET @sql = CONCAT('ALTER TABLE `', p_table, '` ADD UNIQUE INDEX `', p_index, '` (', p_columns, ')');
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END//
DELIMITER ;

CALL add_unique_index_if_not_exists('red_packet_record', 'uk_red_packet_user', 'red_packet_id, user_id');

DROP PROCEDURE IF EXISTS add_unique_index_if_not_exists;

-- 验证
DESC `red_packet_record`;
