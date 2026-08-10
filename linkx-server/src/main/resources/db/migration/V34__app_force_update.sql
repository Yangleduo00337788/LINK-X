-- 作者：yangleduo
-- 客户端版本强更与最低支持版本（灰度渠道沿用 app_channel）
ALTER TABLE `sys_runtime_setting`
  ADD COLUMN `force_update` tinyint(1) NOT NULL DEFAULT 0 COMMENT '有更新时是否强制升级' AFTER `download_url`,
  ADD COLUMN `min_supported_version` varchar(32) NULL COMMENT '低于此版本强制升级（可空）' AFTER `force_update`;
