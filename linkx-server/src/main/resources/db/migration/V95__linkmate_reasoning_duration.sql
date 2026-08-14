-- 灵伴消息：深度思考阶段耗时
ALTER TABLE ai_chat_message
    ADD COLUMN reasoning_duration_ms INT NULL COMMENT '深度思考阶段耗时毫秒' AFTER response_duration_ms;
