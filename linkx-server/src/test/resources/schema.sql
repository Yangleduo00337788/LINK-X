-- ============================================================
-- LinkX 集成测试数据库 schema（H2 MySQL 兼容模式）
-- 由 spring.sql.init 在每次上下文启动时执行；使用 IF NOT EXISTS 保证幂等。
-- 主键均为应用层雪花 ID，无需自增；逻辑删除列 deleted 默认 0。
-- ============================================================

-- 系统用户表
CREATE TABLE IF NOT EXISTS sys_user (
  id BIGINT NOT NULL PRIMARY KEY,
  username VARCHAR(64) NOT NULL,
  password VARCHAR(255) NOT NULL,
  nickname VARCHAR(64) NOT NULL,
  avatar VARCHAR(255),
  signature VARCHAR(255),
  gender VARCHAR(8),
  birthday BIGINT,
  country VARCHAR(64),
  province VARCHAR(64),
  region VARCHAR(64),
  email VARCHAR(128),
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME,
  update_time DATETIME,
  create_by BIGINT,
  update_by BIGINT,
  deleted TINYINT NOT NULL DEFAULT 0
);

-- 好友关系表
CREATE TABLE IF NOT EXISTS sys_user_relation (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  friend_id BIGINT NOT NULL,
  remark VARCHAR(64),
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME,
  update_time DATETIME,
  deleted TINYINT NOT NULL DEFAULT 0
);

-- 好友申请表
CREATE TABLE IF NOT EXISTS sys_friend_request (
  id BIGINT NOT NULL PRIMARY KEY,
  from_user_id BIGINT NOT NULL,
  to_user_id BIGINT NOT NULL,
  message VARCHAR(255),
  status TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME,
  update_time DATETIME,
  deleted TINYINT NOT NULL DEFAULT 0
);

-- IM 会话表
CREATE TABLE IF NOT EXISTS im_conversation (
  id BIGINT NOT NULL PRIMARY KEY,
  type TINYINT NOT NULL DEFAULT 1,
  private_key VARCHAR(64),
  name VARCHAR(128),
  avatar VARCHAR(255),
  owner_id BIGINT,
  last_message_content VARCHAR(500),
  last_message_time DATETIME,
  create_time DATETIME,
  update_time DATETIME,
  deleted TINYINT NOT NULL DEFAULT 0
);

-- IM 会话成员表
CREATE TABLE IF NOT EXISTS im_conversation_member (
  id BIGINT NOT NULL PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role TINYINT NOT NULL DEFAULT 0,
  remark VARCHAR(64),
  create_time DATETIME,
  update_time DATETIME,
  deleted TINYINT NOT NULL DEFAULT 0
);

-- IM 消息表
CREATE TABLE IF NOT EXISTS im_message (
  id BIGINT NOT NULL PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  sender_id BIGINT NOT NULL,
  type VARCHAR(20) NOT NULL DEFAULT 'text',
  content TEXT,
  file_name VARCHAR(255),
  file_size BIGINT,
  file_url VARCHAR(500),
  create_time DATETIME,
  deleted TINYINT NOT NULL DEFAULT 0
);

-- 登录审计表
CREATE TABLE IF NOT EXISTS sys_login_audit (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT,
  username VARCHAR(64) NOT NULL,
  ip VARCHAR(64),
  user_agent VARCHAR(512),
  success TINYINT NOT NULL DEFAULT 0,
  reason VARCHAR(255),
  create_time DATETIME
);

-- 红包表
CREATE TABLE IF NOT EXISTS red_packet (
  id BIGINT NOT NULL PRIMARY KEY,
  sender_id BIGINT,
  conversation_id BIGINT,
  type VARCHAR(20) NOT NULL DEFAULT 'normal',
  total_amount DECIMAL(10,2),
  total_count INT,
  remaining_amount DECIMAL(10,2),
  remaining_count INT,
  greeting VARCHAR(255),
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  expire_time DATETIME,
  create_time DATETIME,
  version BIGINT NOT NULL DEFAULT 0
);

-- 红包领取记录表
CREATE TABLE IF NOT EXISTS red_packet_record (
  id BIGINT NOT NULL PRIMARY KEY,
  red_packet_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  amount DECIMAL(10,2),
  is_lucky TINYINT DEFAULT 0,
  create_time DATETIME
);

-- 用户余额表
CREATE TABLE IF NOT EXISTS user_balance (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  balance DECIMAL(10,2) NOT NULL DEFAULT 0,
  frozen DECIMAL(10,2) NOT NULL DEFAULT 0,
  total_recharge DECIMAL(10,2) NOT NULL DEFAULT 0,
  total_withdraw DECIMAL(10,2) NOT NULL DEFAULT 0,
  create_time DATETIME,
  update_time DATETIME
);

-- 余额变动日志表
CREATE TABLE IF NOT EXISTS balance_log (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  type VARCHAR(20) NOT NULL,
  amount DECIMAL(10,2) NOT NULL,
  balance_before DECIMAL(10,2),
  balance_after DECIMAL(10,2),
  biz_type VARCHAR(50),
  biz_id VARCHAR(64),
  remark VARCHAR(255),
  operator_id BIGINT,
  create_time DATETIME
);

-- 朋友圈动态表
CREATE TABLE IF NOT EXISTS moments_post (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  content TEXT,
  create_time DATETIME,
  deleted TINYINT NOT NULL DEFAULT 0
);

-- 朋友圈评论表
CREATE TABLE IF NOT EXISTS moments_comment (
  id BIGINT NOT NULL PRIMARY KEY,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  content TEXT,
  parent_id BIGINT,
  create_time DATETIME,
  deleted TINYINT NOT NULL DEFAULT 0
);

-- 朋友圈点赞表
CREATE TABLE IF NOT EXISTS moments_like (
  id BIGINT NOT NULL PRIMARY KEY,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  create_time DATETIME,
  deleted TINYINT NOT NULL DEFAULT 0
);

-- 朋友圈图片表
CREATE TABLE IF NOT EXISTS moments_image (
  id BIGINT NOT NULL PRIMARY KEY,
  post_id BIGINT NOT NULL,
  url VARCHAR(500),
  sort_order INT DEFAULT 0
);

-- 日历事件表
CREATE TABLE IF NOT EXISTS calendar_event (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  title VARCHAR(128),
  date VARCHAR(20),
  time VARCHAR(10),
  color VARCHAR(20),
  create_time DATETIME,
  update_time DATETIME,
  deleted TINYINT NOT NULL DEFAULT 0
);

-- 笔记表
CREATE TABLE IF NOT EXISTS note (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  title VARCHAR(255),
  content TEXT,
  type VARCHAR(20) NOT NULL DEFAULT 'note',
  create_time DATETIME,
  update_time DATETIME,
  deleted TINYINT NOT NULL DEFAULT 0
);

-- 用户反馈表
CREATE TABLE IF NOT EXISTS sys_feedback (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT,
  username VARCHAR(64),
  type VARCHAR(32),
  content TEXT,
  contact VARCHAR(128),
  status VARCHAR(20) DEFAULT 'pending',
  create_time DATETIME
);

-- 消息通知表
CREATE TABLE IF NOT EXISTS message_notification (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  sender_id BIGINT,
  sender_name VARCHAR(64),
  sender_avatar VARCHAR(255),
  type VARCHAR(32),
  related_id BIGINT,
  content VARCHAR(255),
  read_status TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME,
  deleted TINYINT NOT NULL DEFAULT 0
);

-- 设备会话表
CREATE TABLE IF NOT EXISTS sys_device_session (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  device_id VARCHAR(128),
  device_name VARCHAR(128),
  device_type VARCHAR(32),
  ip VARCHAR(64),
  user_agent VARCHAR(512),
  last_active DATETIME,
  create_time DATETIME
);

-- 用户偏好设置表（per-user，一行一用户）
CREATE TABLE IF NOT EXISTS user_preference (
  user_id BIGINT NOT NULL PRIMARY KEY,
  auto_start TINYINT NOT NULL DEFAULT 0,
  sound_notify TINYINT NOT NULL DEFAULT 1,
  message_detail TINYINT NOT NULL DEFAULT 1,
  notify_at_me TINYINT NOT NULL DEFAULT 1,
  notify_sound TINYINT NOT NULL DEFAULT 0,
  privacy_verify_friend TINYINT NOT NULL DEFAULT 1,
  privacy_allow_stranger TINYINT NOT NULL DEFAULT 0,
  privacy_show_online TINYINT NOT NULL DEFAULT 1,
  language VARCHAR(16) NOT NULL DEFAULT 'zh-CN',
  chat_background VARCHAR(32) NOT NULL DEFAULT 'default',
  notify_tone VARCHAR(32) NOT NULL DEFAULT 'default',
  create_time DATETIME,
  update_time DATETIME
);

-- 群邀请表
CREATE TABLE IF NOT EXISTS group_invitation (
  id BIGINT NOT NULL PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  inviter_user_id BIGINT NOT NULL,
  invitee_user_id BIGINT NOT NULL,
  message VARCHAR(255),
  status TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME,
  update_time DATETIME
);
CREATE INDEX IF NOT EXISTS idx_group_invitation_invitee ON group_invitation(invitee_user_id, status);

CREATE TABLE IF NOT EXISTS group_asset (
  id BIGINT NOT NULL PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  uploader_id BIGINT NOT NULL,
  type VARCHAR(20) NOT NULL,
  title VARCHAR(255),
  content TEXT,
  file_name VARCHAR(255),
  file_size BIGINT,
  file_key VARCHAR(500),
  message_id BIGINT,
  download_count INT NOT NULL DEFAULT 0,
  create_time DATETIME,
  update_time DATETIME,
  deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS group_announcement (
  id BIGINT NOT NULL PRIMARY KEY,
  conversation_id BIGINT NOT NULL,
  content TEXT NOT NULL,
  publisher_id BIGINT NOT NULL,
  pinned TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME,
  update_time DATETIME,
  deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS favorite (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  title VARCHAR(200),
  content TEXT NOT NULL,
  type VARCHAR(20) NOT NULL DEFAULT 'note',
  source_type VARCHAR(32),
  source_id VARCHAR(64),
  create_time DATETIME,
  update_time DATETIME,
  deleted TINYINT NOT NULL DEFAULT 0
);
