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
  totp_enabled TINYINT NOT NULL DEFAULT 0,
  totp_secret VARCHAR(128),
  totp_confirmed_at DATETIME,
  status TINYINT NOT NULL DEFAULT 1,
  dept_id BIGINT,
  auto_locked_until DATETIME,
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
  group_name VARCHAR(32),
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
  client_msg_id VARCHAR(128) COMMENT '客户端幂等ID（与发送者组成唯一约束）',
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
CREATE UNIQUE INDEX IF NOT EXISTS uk_sender_client_msg ON im_message(sender_id, client_msg_id);

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
  conversation_type INT,
  type VARCHAR(20) NOT NULL DEFAULT 'normal',
  total_amount DECIMAL(10,2),
  total_count INT,
  remaining_amount DECIMAL(10,2),
  remaining_count INT,
  greeting VARCHAR(255),
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  expire_time DATETIME,
  client_msg_id VARCHAR(128),
  create_time DATETIME,
  version BIGINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0,
  CONSTRAINT uk_sender_client_msg UNIQUE (sender_id, client_msg_id)
);

-- 红包领取记录表（与生产 uk_red_packet_user 对齐，防重复领取）
CREATE TABLE IF NOT EXISTS red_packet_record (
  id BIGINT NOT NULL PRIMARY KEY,
  red_packet_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  amount DECIMAL(10,2),
  is_lucky TINYINT DEFAULT 0,
  create_time DATETIME,
  CONSTRAINT uk_red_packet_user UNIQUE (red_packet_id, user_id)
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
  create_time DATETIME,
  deleted TINYINT NOT NULL DEFAULT 0,
  -- 幂等键唯一索引
  UNIQUE KEY uk_balance_idem (user_id, biz_type, biz_id)
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
  reply TEXT,
  reply_time DATETIME,
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
  scene VARCHAR(16) NOT NULL DEFAULT 'meeting',
  creator_id BIGINT NOT NULL,
  conversation_id BIGINT,
  status TINYINT NOT NULL DEFAULT 0,
  max_participants INT NOT NULL DEFAULT 9,
  start_time DATETIME,
  end_time DATETIME,
  password VARCHAR(100),
  lobby_enabled TINYINT NOT NULL DEFAULT 0,
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
  admit_status TINYINT NOT NULL DEFAULT 1,
  join_time DATETIME,
  leave_time DATETIME,
  create_time DATETIME
);

-- MinIO 对象属主登记
CREATE TABLE IF NOT EXISTS sys_object_ownership (
  object_key VARCHAR(512) NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  create_time DATETIME,
  update_time DATETIME
);

-- ============================================================
-- RBAC 角色权限表（与 init.sql 对齐）
-- ============================================================

-- 系统角色表
CREATE TABLE IF NOT EXISTS sys_role (
  id BIGINT NOT NULL PRIMARY KEY,
  role_code VARCHAR(64) NOT NULL,
  role_name VARCHAR(64) NOT NULL,
  description VARCHAR(255),
  status TINYINT NOT NULL DEFAULT 1,
  data_scope TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME,
  update_time DATETIME,
  create_by BIGINT,
  update_by BIGINT,
  deleted TINYINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_role_code ON sys_role(role_code);

CREATE TABLE IF NOT EXISTS sys_dept (
  id BIGINT NOT NULL PRIMARY KEY,
  parent_id BIGINT NOT NULL DEFAULT 0,
  name VARCHAR(64) NOT NULL,
  sort_order INT NOT NULL DEFAULT 0,
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME,
  update_time DATETIME,
  create_by BIGINT,
  update_by BIGINT,
  deleted TINYINT NOT NULL DEFAULT 0
);

INSERT IGNORE INTO sys_dept (id, parent_id, name, sort_order, status) VALUES
  (1, 0, '总部', 1, 1),
  (2, 1, '运营部', 1, 1),
  (3, 1, '审核部', 2, 1),
  (4, 1, '安全部', 3, 1);

-- 角色自定义数据范围部门
CREATE TABLE IF NOT EXISTS sys_role_dept (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  role_id BIGINT NOT NULL,
  dept_id BIGINT NOT NULL,
  create_time DATETIME
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_role_dept ON sys_role_dept(role_id, dept_id);

-- 用户-角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  role_id BIGINT NOT NULL,
  create_time DATETIME,
  create_by BIGINT,
  deleted TINYINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_user_role ON sys_user_role(user_id, role_id);

-- 系统权限表
CREATE TABLE IF NOT EXISTS sys_permission (
  id BIGINT NOT NULL PRIMARY KEY,
  permission_code VARCHAR(128) NOT NULL,
  permission_name VARCHAR(64) NOT NULL,
  resource_type VARCHAR(16) NOT NULL DEFAULT 'api',
  resource_path VARCHAR(255),
  description VARCHAR(255),
  status TINYINT NOT NULL DEFAULT 1,
  create_time DATETIME,
  update_time DATETIME,
  deleted TINYINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_perm_code ON sys_permission(permission_code);

-- 角色-权限关联表
CREATE TABLE IF NOT EXISTS sys_role_permission (
  id BIGINT NOT NULL PRIMARY KEY,
  role_id BIGINT NOT NULL,
  permission_id BIGINT NOT NULL,
  create_time DATETIME,
  create_by BIGINT,
  deleted TINYINT NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_role_permission ON sys_role_permission(role_id, permission_id);

-- RBAC 预置数据：admin / user 两个角色 + 基础权限
INSERT IGNORE INTO sys_role (id, role_code, role_name, description, status, data_scope, create_by) VALUES
  (1001, 'admin', '系统管理员', '拥有全部权限', 1, 1, NULL),
  (1002, 'user', '普通用户', '注册用户默认角色', 1, 2, NULL),
  (1003, 'ops_admin', '运营管理员', '仪表盘、用户查看、反馈、公告、Banner、统计', 1, 1, NULL),
  (1004, 'audit_admin', '审核管理员', '用户处置、内容审核、敏感词、风控、黑名单、设备、日志', 1, 1, NULL),
  (1005, 'security_admin', '安全管理员', '登录审计、设备管理、风控、黑名单', 1, 1, NULL),
  (1006, 'readonly_observer', '只读观察员', '只读查看仪表盘、用户、日志、统计与审核列表', 1, 1, NULL);

INSERT IGNORE INTO sys_permission (id, permission_code, permission_name, resource_type, resource_path, description, status) VALUES
  (2001, '*', '全部权限', 'api', '*', '通配符权限，拥有全部能力', 1),
  (2002, 'rbac:role:create', '创建角色', 'api', '/rbac/role', '创建系统角色', 1),
  (2003, 'rbac:role:list', '查询角色列表', 'api', '/rbac/role', '查询角色列表', 1),
  (2004, 'rbac:user:grant', '分配用户角色', 'api', '/rbac/user/*/role/*', '为用户分配角色', 1),
  (2005, 'rbac:user:revoke', '移除用户角色', 'api', '/rbac/user/*/role/*', '移除用户角色', 1),
  (2006, 'rbac:user:permissions', '查询用户权限', 'api', '/rbac/user/*/permissions', '查询用户权限列表', 1);

-- admin 角色拥有全部权限
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, create_by) VALUES
  (1, 1001, 2001, NULL),
  (2, 1001, 2002, NULL),
  (3, 1001, 2003, NULL),
  (4, 1001, 2004, NULL),
  (5, 1001, 2005, NULL),
  (6, 1001, 2006, NULL);

-- 管理端菜单（测试最小表结构，避免 AdminMenuMapper 缺表）
CREATE TABLE IF NOT EXISTS sys_admin_menu (
  id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  parent_id BIGINT NOT NULL DEFAULT 0,
  name VARCHAR(64) NOT NULL,
  title VARCHAR(64) NOT NULL,
  path VARCHAR(255) NOT NULL,
  component VARCHAR(255),
  redirect VARCHAR(255),
  icon VARCHAR(64),
  menu_type VARCHAR(16) NOT NULL DEFAULT 'menu',
  permission_code VARCHAR(128),
  sort_order INT NOT NULL DEFAULT 0,
  hidden TINYINT NOT NULL DEFAULT 0,
  cacheable TINYINT NOT NULL DEFAULT 1,
  external_link TINYINT NOT NULL DEFAULT 0,
  keep_alive TINYINT NOT NULL DEFAULT 1,
  status TINYINT NOT NULL DEFAULT 1,
  remark VARCHAR(255),
  created_by BIGINT,
  created_at DATETIME,
  updated_by BIGINT,
  updated_at DATETIME,
  deleted TINYINT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_admin_role_menu (
  id BIGINT NOT NULL PRIMARY KEY AUTO_INCREMENT,
  role_id BIGINT NOT NULL,
  menu_id BIGINT NOT NULL,
  created_at DATETIME
);

CREATE TABLE IF NOT EXISTS sys_runtime_setting (
  id BIGINT NOT NULL PRIMARY KEY,
  admin_captcha_enabled TINYINT NOT NULL DEFAULT 1,
  admin_login_max_attempts INT NOT NULL DEFAULT 5,
  admin_lock_duration_minutes INT NOT NULL DEFAULT 10,
  admin_totp_required TINYINT NOT NULL DEFAULT 0,
  client_captcha_enabled TINYINT NOT NULL DEFAULT 1,
  client_register_enabled TINYINT NOT NULL DEFAULT 1,
  client_forgot_password_email_enabled TINYINT NOT NULL DEFAULT 1,
  client_login_max_attempts INT NOT NULL DEFAULT 5,
  client_lock_duration_minutes INT NOT NULL DEFAULT 10,
  password_min_length INT NOT NULL DEFAULT 8,
  password_max_length INT NOT NULL DEFAULT 64,
  password_require_upper_lower TINYINT NOT NULL DEFAULT 0,
  password_require_digit TINYINT NOT NULL DEFAULT 1,
  password_require_special TINYINT NOT NULL DEFAULT 0,
  app_version VARCHAR(32) NOT NULL DEFAULT '1.0.0',
  app_channel VARCHAR(32) NOT NULL DEFAULT 'stable',
  release_notes VARCHAR(2000),
  download_url VARCHAR(512),
  max_upload_bytes BIGINT NOT NULL DEFAULT 104857600,
  update_by BIGINT,
  create_time DATETIME,
  update_time DATETIME
);

-- 内容审核任务（仪表盘 pendingReviews 依赖）
CREATE TABLE IF NOT EXISTS sys_review_task (
  id BIGINT NOT NULL PRIMARY KEY,
  source_type VARCHAR(32) NOT NULL,
  target_type VARCHAR(32),
  target_id VARCHAR(64),
  reporter_user_id BIGINT,
  reporter_username VARCHAR(64),
  title VARCHAR(128),
  content_snapshot CLOB NOT NULL,
  risk_level VARCHAR(16) NOT NULL DEFAULT 'medium',
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
  feedback_id BIGINT,
  assignee_id BIGINT,
  resolution VARCHAR(1000),
  resolved_by BIGINT,
  resolved_at DATETIME,
  create_time DATETIME,
  update_time DATETIME
);

-- 风险事件（仪表盘 riskEvents / 管理端风险处置依赖）
CREATE TABLE IF NOT EXISTS sys_risk_event (
  id BIGINT NOT NULL PRIMARY KEY,
  event_type VARCHAR(32) NOT NULL,
  title VARCHAR(128) NOT NULL,
  detail CLOB,
  risk_level VARCHAR(16) NOT NULL DEFAULT 'medium',
  status VARCHAR(16) NOT NULL DEFAULT 'pending',
  user_id BIGINT,
  username VARCHAR(64),
  target_resource_id VARCHAR(128),
  target_resource_type VARCHAR(64),
  ip VARCHAR(64),
  extra_data VARCHAR(2048),
  audit_log_id BIGINT,
  resolution VARCHAR(1000),
  handled_by BIGINT,
  handled_at DATETIME,
  create_time DATETIME,
  update_time DATETIME
);

-- 管理端平台黑名单
CREATE TABLE IF NOT EXISTS sys_admin_blacklist (
  id BIGINT NOT NULL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  username VARCHAR(64),
  nickname VARCHAR(64),
  reason VARCHAR(255),
  status VARCHAR(16) NOT NULL DEFAULT 'active',
  created_by BIGINT,
  released_by BIGINT,
  released_at DATETIME,
  release_reason VARCHAR(255),
  create_time DATETIME,
  update_time DATETIME
);

-- =============================================================================
-- 管理端角色冒烟：最小菜单 / 权限码 / 绑定（对齐 V5+V28+V30）
-- =============================================================================
INSERT IGNORE INTO sys_admin_menu
(id, parent_id, name, title, path, component, icon, menu_type, permission_code, sort_order, hidden, cacheable, external_link, keep_alive, status, deleted) VALUES
(1,  0, 'dashboard',   '仪表盘',   '/admin/dashboard',  'views/DashboardView',   'Dashboard', 'menu', 'admin:dashboard:view', 1, 0, 1, 0, 1, 1, 0),
(2,  0, 'user',        '用户管理', '/admin/users',      'views/UserListView',    'People',    'menu', 'admin:user:list',      2, 0, 1, 0, 1, 1, 0),
(7,  0, 'log',         '日志中心', '/admin/logs',       NULL,                    'History',   'dir',  NULL,                   4, 0, 1, 0, 1, 1, 0),
(8,  7, 'audit-log',   '操作日志', '/admin/audit-logs', 'views/AuditLogView',    'Document',  'menu', 'admin:audit:list',     1, 0, 1, 0, 1, 1, 0),
(9,  7, 'login-log',   '登录日志', '/admin/login-logs', 'views/LoginLogView',    'LogIn',     'menu', 'admin:login-log:list', 2, 0, 1, 0, 1, 1, 0),
(10, 0, 'feedback',    '反馈管理', '/admin/feedback',   'views/FeedbackListView','Chatbox',   'menu', 'admin:feedback:list',  5, 0, 1, 0, 1, 1, 0),
(11, 0, 'settings',    '系统配置', '/admin/settings',   'views/SettingView',     'Settings',  'menu', 'admin:setting:view',   6, 0, 1, 0, 1, 1, 0),
(13, 0, 'review',      '审核中心', '/admin/review',     NULL,                    'Shield',    'dir',  NULL,                   5, 0, 1, 0, 1, 1, 0),
(14, 13,'review-task', '违规内容', '/admin/reviews',    'views/ReviewListView',  'Document',  'menu', 'admin:review:list',    1, 0, 1, 0, 1, 1, 0),
(16, 0, 'notices',     '公告管理', '/admin/notices',    'views/NoticeView',      'Bell',      'menu', 'admin:notice:list',    7, 0, 1, 0, 1, 1, 0),
(18, 0, 'statistics',  '统计分析', '/admin/statistics', 'views/StatisticsView',  'Chart',     'menu', 'admin:statistics:view',9, 0, 1, 0, 1, 1, 0),
(19, 7, 'risk-event',  '风险事件', '/admin/risk-events','views/RiskEventView',   'Warning',   'menu', 'admin:risk-event:list',3, 0, 1, 0, 1, 1, 0),
(20, 0, 'blacklist',   '黑名单管理','/admin/blacklist', 'views/BlacklistView',   'Ban',       'menu', 'admin:blacklist:list', 2, 0, 1, 0, 1, 1, 0),
(22, 0, 'devices',     '设备管理', '/admin/devices',    'views/DeviceListView',  'Phone',     'menu', 'admin:device:list',    2, 0, 1, 0, 1, 1, 0);

INSERT IGNORE INTO sys_admin_role_menu (role_id, menu_id) VALUES
-- admin 全量（测试用最小集）
(1001, 1),(1001, 2),(1001, 7),(1001, 8),(1001, 9),(1001, 10),(1001, 11),
(1001, 13),(1001, 14),(1001, 16),(1001, 18),(1001, 19),(1001, 20),(1001, 22),
-- ops
(1003, 1),(1003, 2),(1003, 10),(1003, 16),(1003, 18),
-- audit
(1004, 1),(1004, 2),(1004, 7),(1004, 8),(1004, 9),(1004, 19),
(1004, 13),(1004, 14),(1004, 20),(1004, 22),
-- security
(1005, 1),(1005, 2),(1005, 7),(1005, 8),(1005, 9),(1005, 19),(1005, 20),(1005, 22),
-- readonly
(1006, 1),(1006, 2),(1006, 7),(1006, 8),(1006, 9),(1006, 19),(1006, 10),(1006, 13),(1006, 14),(1006, 18),(1006, 22);

INSERT IGNORE INTO sys_permission (id, permission_code, permission_name, resource_type, resource_path, description, status) VALUES
(2101,'admin:dashboard:view','查看仪表盘','page','/admin/dashboard','仪表盘',1),
(2102,'admin:user:list','查看用户列表','page','/admin/users','用户管理',1),
(2103,'admin:user:view','查看用户详情','button',NULL,'用户详情',1),
(2105,'admin:user:freeze','冻结用户','button',NULL,'冻结',1),
(2107,'admin:user:ban','封禁用户','button',NULL,'封禁',1),
(2173,'admin:user:reset-password','重置用户密码','button',NULL,'重置密码并吊销会话',1),
(2121,'admin:audit:list','查看操作日志','page','/admin/audit-logs','操作日志',1),
(2123,'admin:feedback:list','查看反馈列表','page','/admin/feedback','反馈管理',1),
(2124,'admin:feedback:reply','回复反馈','button',NULL,'回复反馈',1),
(2126,'admin:setting:view','查看系统配置','page','/admin/settings','系统配置',1),
(2127,'admin:setting:edit','编辑系统配置','button',NULL,'编辑配置',1),
(2129,'admin:review:list','查看审核列表','page','/admin/reviews','内容审核',1),
(2130,'admin:review:approve','审核通过','button',NULL,'审核通过',1),
(2136,'admin:notice:list','查看公告列表','page','/admin/notices','公告管理',1),
(2138,'admin:notice:create','新增公告','button',NULL,'新增公告',1),
(2144,'admin:statistics:view','查看统计分析','page','/admin/statistics','统计中心',1),
(2146,'admin:risk-event:list','查看风险事件','page','/admin/risk-events','风险事件列表',1),
(2148,'admin:risk-event:handle','处置风险事件','button',NULL,'风险事件处置',1),
(2149,'admin:blacklist:list','查看黑名单','page','/admin/blacklist','黑名单列表',1),
(2153,'admin:user:export','导出用户数据','button',NULL,'用户列表导出',1),
(2156,'admin:risk-event:export','导出风险事件','button',NULL,'风险事件导出',1),
(2171,'admin:device:list','查看设备列表','page','/admin/devices','设备会话列表',1),
(2172,'admin:device:kick','强制设备下线','button',NULL,'踢设备下线',1),
(2174,'admin:menu:reorder','调整菜单排序','button',NULL,'菜单拖拽/上下排序',1),
(2176,'admin:device:export','导出设备列表','button',NULL,'设备会话导出',1),
(2177,'admin:blacklist:export','导出黑名单','button',NULL,'黑名单导出',1),
(2178,'admin:dept:list','查看部门','page','/admin/depts','部门管理',1),
(2179,'admin:dept:create','新增部门','button',NULL,'新增部门',1),
(2180,'admin:dept:edit','编辑部门','button',NULL,'编辑部门',1),
(2181,'admin:dept:delete','删除部门','button',NULL,'删除部门',1);

-- ops: 查看 + 反馈/公告/统计 + 用户导出
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, create_by, deleted) VALUES
(293101, 1003, 2101, NULL, 0),(293102, 1003, 2102, NULL, 0),(293103, 1003, 2103, NULL, 0),
(293153, 1003, 2153, NULL, 0),
(293123, 1003, 2123, NULL, 0),(293124, 1003, 2124, NULL, 0),
(293136, 1003, 2136, NULL, 0),(293138, 1003, 2138, NULL, 0),
(293144, 1003, 2144, NULL, 0);

-- audit: 用户处置 + 审核 + 风险 + 黑名单 + 设备 + 日志 + 导出
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, create_by, deleted) VALUES
(294101, 1004, 2101, NULL, 0),(294102, 1004, 2102, NULL, 0),(294103, 1004, 2103, NULL, 0),
(294105, 1004, 2105, NULL, 0),(294107, 1004, 2107, NULL, 0),(294173, 1004, 2173, NULL, 0),
(294121, 1004, 2121, NULL, 0),(294129, 1004, 2129, NULL, 0),(294130, 1004, 2130, NULL, 0),
(294146, 1004, 2146, NULL, 0),(294148, 1004, 2148, NULL, 0),(294156, 1004, 2156, NULL, 0),
(294149, 1004, 2149, NULL, 0),(294171, 1004, 2171, NULL, 0),(294172, 1004, 2172, NULL, 0),
(294176, 1004, 2176, NULL, 0),(294177, 1004, 2177, NULL, 0);

-- security: 日志/风险/黑名单/设备/用户处置 + 导出
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, create_by, deleted) VALUES
(295101, 1005, 2101, NULL, 0),(295102, 1005, 2102, NULL, 0),(295103, 1005, 2103, NULL, 0),
(295105, 1005, 2105, NULL, 0),(295107, 1005, 2107, NULL, 0),(295173, 1005, 2173, NULL, 0),
(295121, 1005, 2121, NULL, 0),(295146, 1005, 2146, NULL, 0),(295148, 1005, 2148, NULL, 0),(295156, 1005, 2156, NULL, 0),
(295149, 1005, 2149, NULL, 0),(295171, 1005, 2171, NULL, 0),(295172, 1005, 2172, NULL, 0),
(295176, 1005, 2176, NULL, 0),(295177, 1005, 2177, NULL, 0);

-- readonly: 查看 + 导出（无写）
INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id, create_by, deleted) VALUES
(296101, 1006, 2101, NULL, 0),(296102, 1006, 2102, NULL, 0),(296103, 1006, 2103, NULL, 0),
(296153, 1006, 2153, NULL, 0),
(296121, 1006, 2121, NULL, 0),(296123, 1006, 2123, NULL, 0),(296129, 1006, 2129, NULL, 0),
(296144, 1006, 2144, NULL, 0),(296146, 1006, 2146, NULL, 0),(296171, 1006, 2171, NULL, 0),
(296176, 1006, 2176, NULL, 0);

