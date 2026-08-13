-- 灵伴（LinkMate）AI 对话会话与消息
CREATE TABLE IF NOT EXISTS ai_chat_session (
    id              BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花 ID',
    user_id         BIGINT       NOT NULL COMMENT '用户 ID',
    title           VARCHAR(200) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_ai_session_user_update (user_id, update_time)
) COMMENT '灵伴 AI 对话会话';

CREATE TABLE IF NOT EXISTS ai_chat_message (
    id              BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花 ID',
    session_id      BIGINT       NOT NULL COMMENT '会话 ID',
    user_id         BIGINT       NOT NULL COMMENT '用户 ID',
    role            VARCHAR(20)  NOT NULL COMMENT 'user / assistant / system',
    content         TEXT         NOT NULL COMMENT '消息正文',
    token_count     INT          NOT NULL DEFAULT 0 COMMENT '估算 token 数',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ai_msg_session_time (session_id, create_time)
) COMMENT '灵伴 AI 对话消息';
