-- 修复数据库表缺失列（综合修复）
-- 通过对比实体类与表结构，补齐所有缺失的列
-- MySQL 5.7/8.0 不支持 ALTER TABLE ADD COLUMN IF NOT EXISTS，需用存储过程

USE `linkx`;

-- ============================================
-- 通用存储过程：检查列是否存在，不存在则添加
-- ============================================
DROP PROCEDURE IF EXISTS add_column_if_not_exists;

DELIMITER //
CREATE PROCEDURE add_column_if_not_exists(
  IN p_table VARCHAR(128),
  IN p_column VARCHAR(128),
  IN p_definition TEXT
)
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = p_table
      AND COLUMN_NAME = p_column
  ) THEN
    SET @sql = CONCAT('ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition);
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
  END IF;
END//
DELIMITER ;

-- ============================================
-- IM 模块表修复
-- ============================================

-- im_conversation: 补齐群聊相关字段
CALL add_column_if_not_exists('im_conversation', 'name', "VARCHAR(100) DEFAULT NULL COMMENT '群名称（群聊用）' AFTER `private_key`");
CALL add_column_if_not_exists('im_conversation', 'avatar', "VARCHAR(500) DEFAULT NULL COMMENT '群头像URL' AFTER `name`");
CALL add_column_if_not_exists('im_conversation', 'announcement', "TEXT DEFAULT NULL COMMENT '群公告' AFTER `avatar`");
CALL add_column_if_not_exists('im_conversation', 'owner_id', "BIGINT DEFAULT NULL COMMENT '群主ID（群聊用）' AFTER `announcement`");

-- im_conversation_member: 补齐 role 字段
CALL add_column_if_not_exists('im_conversation_member', 'role', "VARCHAR(20) DEFAULT NULL COMMENT '角色(owner/admin/member)' AFTER `user_id`");

-- im_message: 补齐 voice_duration 字段
CALL add_column_if_not_exists('im_message', 'voice_duration', "INT DEFAULT NULL COMMENT '语音时长(秒)' AFTER `file_url`");

-- ============================================
-- 清理：删除存储过程
-- ============================================
DROP PROCEDURE IF EXISTS add_column_if_not_exists;

-- ============================================
-- 验证：检查 im_conversation_member 表结构
-- ============================================
DESC `im_conversation_member`;

-- ============================================
-- 验证：检查 im_conversation 表结构
-- ============================================
DESC `im_conversation`;

-- ============================================
-- 验证：检查 im_message 表结构
-- ============================================
DESC `im_message`;
