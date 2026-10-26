-- 作者：yangleduo
-- 管理端未标注接口曾走 global-default 限流桶，浏览后台会刷出大量待处置 RATE_LIMIT。
-- 拦截器已跳过该桶；此处忽略存量噪声，登录/业务限流事件不改。

UPDATE `sys_risk_event`
SET `status` = 'ignored',
    `resolution` = CASE
        WHEN `resolution` IS NULL OR `resolution` = '' THEN '系统清理：管理端默认限流不再记为风险事件'
        ELSE `resolution`
    END,
    `handled_at` = COALESCE(`handled_at`, NOW()),
    `update_time` = NOW()
WHERE `status` = 'pending'
  AND `event_type` = 'RATE_LIMIT'
  AND `extra_data` = 'global-default';
