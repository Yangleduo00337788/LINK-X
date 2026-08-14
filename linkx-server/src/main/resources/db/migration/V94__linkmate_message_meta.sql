-- 灵伴消息：推理内容与回复耗时
ALTER TABLE ai_chat_message
    ADD COLUMN reasoning_content TEXT NULL COMMENT '深度思考推理过程' AFTER content,
    ADD COLUMN response_duration_ms INT NULL COMMENT '回复总耗时毫秒' AFTER reasoning_content;
