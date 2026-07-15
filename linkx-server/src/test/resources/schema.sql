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
