-- 作者：yangleduo
-- V88: 版本发布支持平台区分与安装包校验元数据

ALTER TABLE `sys_app_version`
  ADD COLUMN `platform` varchar(16) NOT NULL DEFAULT 'windows' COMMENT 'windows/macos/linux' AFTER `channel`,
  ADD COLUMN `package_sha256` varchar(64) NULL COMMENT '安装包 SHA-256' AFTER `download_url`,
  ADD COLUMN `package_file_name` varchar(255) NULL COMMENT '安装包原始文件名' AFTER `package_sha256`,
  ADD COLUMN `package_size` bigint NULL COMMENT '安装包大小（字节）' AFTER `package_file_name`;

ALTER TABLE `sys_app_version`
  ADD KEY `idx_app_version_platform` (`platform`);
