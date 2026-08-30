-- 安装包格式：同一平台/渠道可同时发布 AppImage、deb 等多种格式
ALTER TABLE `sys_app_version`
  ADD COLUMN `package_format` varchar(16) NOT NULL DEFAULT '' COMMENT '安装包格式：exe/msi/dmg/appimage/deb/rpm' AFTER `platform`;

UPDATE `sys_app_version`
SET `package_format` = 'msi'
WHERE `platform` = 'windows' AND LOWER(`package_file_name`) LIKE '%.msi';

UPDATE `sys_app_version`
SET `package_format` = 'exe'
WHERE `platform` = 'windows' AND (`package_format` = '' OR `package_format` IS NULL);

UPDATE `sys_app_version`
SET `package_format` = 'dmg'
WHERE `platform` = 'macos' AND (`package_format` = '' OR `package_format` IS NULL);

UPDATE `sys_app_version`
SET `package_format` = 'deb'
WHERE `platform` = 'linux' AND LOWER(`package_file_name`) LIKE '%.deb';

UPDATE `sys_app_version`
SET `package_format` = 'rpm'
WHERE `platform` = 'linux' AND LOWER(`package_file_name`) LIKE '%.rpm';

UPDATE `sys_app_version`
SET `package_format` = 'appimage'
WHERE `platform` = 'linux' AND (`package_format` = '' OR `package_format` IS NULL);

UPDATE `sys_app_version`
SET `package_format` = 'exe'
WHERE `package_format` = '' OR `package_format` IS NULL;

CREATE INDEX `idx_app_version_pkg_format` ON `sys_app_version` (`platform`, `channel`, `package_format`, `status`);
