-- privacy_send_read_receipt for user_preference (incremental upgrade)
-- New installs already include this column in init.sql

ALTER TABLE `user_preference`
  ADD COLUMN `privacy_send_read_receipt` tinyint(1) NOT NULL DEFAULT 1
  COMMENT 'send read receipt to others' AFTER `privacy_show_online`;
