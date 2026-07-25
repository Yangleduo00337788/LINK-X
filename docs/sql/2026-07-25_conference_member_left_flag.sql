-- conference_member: 将保留字列名 `left` 重命名为 left_flag
-- 与实体 ConferenceMember.@Column("left_flag") / init.sql 对齐
-- 可重复执行：已迁移则跳过

SET @has_left := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'conference_member'
     AND COLUMN_NAME = 'left'
);
SET @has_left_flag := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
   WHERE TABLE_SCHEMA = DATABASE()
     AND TABLE_NAME = 'conference_member'
     AND COLUMN_NAME = 'left_flag'
);

SET @sql := IF(
  @has_left = 1 AND @has_left_flag = 0,
  'ALTER TABLE `conference_member` CHANGE COLUMN `left` `left_flag` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''是否已离开''',
  'SELECT ''skip: conference_member.left_flag already present or unexpected schema'' AS info'
);

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
