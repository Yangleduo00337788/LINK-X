-- conference: 场景 call=语音/视频电话，meeting=会议（与电话分离）
SET @col_exists := (
  SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'conference'
    AND COLUMN_NAME = 'scene'
);
SET @sql := IF(
  @col_exists = 0,
  'ALTER TABLE `conference` ADD COLUMN `scene` varchar(16) NOT NULL DEFAULT ''meeting'' COMMENT ''call=电话 meeting=会议'' AFTER `type`',
  'SELECT ''skip: conference.scene'' AS info'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
