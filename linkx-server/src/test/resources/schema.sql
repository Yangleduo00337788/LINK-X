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
  phone VARCHAR(32),
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME,
  update_time DATETIME,
  create_by BIGINT,
  update_by BIGINT,
  deleted TINYINT NOT NULL DEFAULT 0
);

-- 敏感操作审计日志
CREATE TABLE IF NOT EXISTS sys_audit_log (
  id BIGINT NOT NULL PRIMARY KEY,
  operation_type VARCHAR(50) NOT NULL,
  description VARCHAR(255),
  user_id BIGINT,
  username VARCHAR(64),
  target_user_id BIGINT,
  target_username VARCHAR(64),
  target_resource_id VARCHAR(128),
  target_resource_type VARCHAR(50),
  ip VARCHAR(64),
  user_agent VARCHAR(512),
  status VARCHAR(20) NOT NULL,
  failure_reason VARCHAR(255),
  extra_data TEXT,
  create_time DATETIME
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
  announcement TEXT,
  owner_id BIGINT,
  mute_all TINYINT NOT NULL DEFAULT 0,
  mute_all_start DATETIME,
  mute_all_end DATETIME,
  join_approval TINYINT NOT NULL DEFAULT 0,
  invite_policy VARCHAR(20) DEFAULT 'anyMember',
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
  role VARCHAR(20),
  remark VARCHAR(64),
  last_read_message_id BIGINT,
  pinned TINYINT NOT NULL DEFAULT 0,
  important TINYINT NOT NULL DEFAULT 0,
  muted TINYINT NOT NULL DEFAULT 0,
  mute_until DATETIME,
  mute TINYINT NOT NULL DEFAULT 0,
  announcement_read TINYINT NOT NULL DEFAULT 0,
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
  client_msg_id VARCHAR(128),
  delivery_status VARCHAR(20) DEFAULT 'pending',
  read_status TINYINT NOT NULL DEFAULT 0,
  voice_duration INT,
  edited TINYINT NOT NULL DEFAULT 0,
  edited_time DATETIME,
  forward_from_message_id BIGINT,
  forward_from_conversation_id BIGINT,
  quote_message_id BIGINT,
  quote_conversation_id BIGINT,
  quote_sender_id BIGINT,
  quote_content TEXT,
  quote_type VARCHAR(20),
  create_time DATETIME,
  deleted TINYINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_im_message_conv_id ON im_message(conversation_id, id);

-- 消息风暴事件表（Redis 限流之外的持久化）
CREATE TABLE IF NOT EXISTS im_message_storm_event (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT,
  conversation_id BIGINT,
  event_type VARCHAR(32) NOT NULL,
  message_count INT,
  window_seconds INT,
  member_count INT,
  create_time DATETIME
);

CREATE INDEX IF NOT EXISTS idx_storm_event_user ON im_message_storm_event(user_id, create_time);

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
  location VARCHAR(255),
  at_users TEXT,
  visibility INT DEFAULT 0,
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
  mentions TEXT,
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
  moments_background VARCHAR(512),
  favorites_view_mode VARCHAR(16) DEFAULT 'grid',
  favorites_sort VARCHAR(16) DEFAULT 'newest',
  quiet_hours_enabled TINYINT NOT NULL DEFAULT 0,
  quiet_hours_start VARCHAR(8) DEFAULT '22:00',
  quiet_hours_end VARCHAR(8) DEFAULT '08:00',
  notify_chat TINYINT NOT NULL DEFAULT 1,
  notify_social TINYINT NOT NULL DEFAULT 1,
  notify_moments TINYINT NOT NULL DEFAULT 1,
  notify_system TINYINT NOT NULL DEFAULT 1,
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
  tags VARCHAR(500),
  file_size BIGINT,
  create_time DATETIME,
  update_time DATETIME,
  deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS favorite_storage (
  user_id BIGINT NOT NULL PRIMARY KEY,
  quota_bytes BIGINT NOT NULL DEFAULT 21474836480,
  used_bytes BIGINT NOT NULL DEFAULT 0,
  item_count INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  create_time DATETIME,
  update_time DATETIME
);

CREATE TABLE IF NOT EXISTS favorite_tag (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  name VARCHAR(64) NOT NULL,
  color VARCHAR(16),
  sort_order INT NOT NULL DEFAULT 0,
  preset TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME,
  update_time DATETIME,
  deleted TINYINT NOT NULL DEFAULT 0
);

-- 网盘存储配额
CREATE TABLE IF NOT EXISTS user_storage (
  user_id BIGINT NOT NULL PRIMARY KEY,
  quota_bytes BIGINT NOT NULL DEFAULT 21474836480,
  used_bytes BIGINT NOT NULL DEFAULT 0,
  file_count INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  create_time DATETIME,
  update_time DATETIME
);

CREATE TABLE IF NOT EXISTS cloud_folder (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  parent_id BIGINT,
  name VARCHAR(255) NOT NULL,
  path VARCHAR(1024) NOT NULL DEFAULT '/',
  sort_order INT NOT NULL DEFAULT 0,
  create_time DATETIME,
  update_time DATETIME,
  deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS cloud_file (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  folder_id BIGINT,
  name VARCHAR(255) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  file_size BIGINT NOT NULL DEFAULT 0,
  file_key VARCHAR(500) NOT NULL,
  content_type VARCHAR(128),
  ext VARCHAR(32),
  category VARCHAR(20) NOT NULL DEFAULT 'other',
  description VARCHAR(1000),
  create_time DATETIME,
  update_time DATETIME,
  deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS cloud_file_tag (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  file_id BIGINT NOT NULL,
  tag_name VARCHAR(64) NOT NULL,
  create_time DATETIME
);

CREATE TABLE IF NOT EXISTS cloud_share (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  share_type VARCHAR(16) NOT NULL,
  target_id BIGINT NOT NULL,
  token VARCHAR(64) NOT NULL,
  password_hash VARCHAR(255),
  expire_at DATETIME,
  max_downloads INT,
  download_count INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME,
  update_time DATETIME
);

CREATE TABLE IF NOT EXISTS cloud_activity (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  target_type VARCHAR(16) NOT NULL,
  target_id BIGINT NOT NULL,
  target_name VARCHAR(255),
  action VARCHAR(32) NOT NULL,
  detail VARCHAR(500),
  create_time DATETIME
);

-- 敏感词表
CREATE TABLE IF NOT EXISTS sys_sensitive_word (
  id BIGINT NOT NULL PRIMARY KEY,
  word VARCHAR(100) NOT NULL,
  category VARCHAR(32) DEFAULT 'general',
  action VARCHAR(20) NOT NULL DEFAULT 'filter',
  replacement VARCHAR(10) DEFAULT '***',
  enabled TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME,
  update_time DATETIME
);

-- 用户黑名单表
CREATE TABLE IF NOT EXISTS sys_user_blacklist (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  blocked_user_id BIGINT NOT NULL,
  reason VARCHAR(255),
  create_time DATETIME
);

-- 多人会议表
CREATE TABLE IF NOT EXISTS conference (
  id BIGINT NOT NULL PRIMARY KEY,
  title VARCHAR(200) DEFAULT '多人会议',
  type VARCHAR(10) NOT NULL DEFAULT 'video',
  creator_id BIGINT NOT NULL,
  conversation_id BIGINT,
  status TINYINT NOT NULL DEFAULT 0,
  max_participants INT NOT NULL DEFAULT 9,
  start_time DATETIME,
  end_time DATETIME,
  password VARCHAR(64),
  create_time DATETIME,
  update_time DATETIME
);

-- 多人会议成员表
CREATE TABLE IF NOT EXISTS conference_member (
  id BIGINT NOT NULL PRIMARY KEY,
  conference_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role VARCHAR(20) NOT NULL DEFAULT 'member',
  muted TINYINT NOT NULL DEFAULT 0,
  video_off TINYINT NOT NULL DEFAULT 0,
  left_flag TINYINT NOT NULL DEFAULT 0,
  join_time DATETIME,
  leave_time DATETIME,
  create_time DATETIME
);
