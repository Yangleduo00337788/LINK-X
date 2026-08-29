-- 发布 LinkX 1.0.1（MinIO 已上传）
START TRANSACTION;

UPDATE `sys_app_version`
SET `status` = 'archived', `update_time` = NOW()
WHERE `deleted` = 0
  AND `status` = 'published'
  AND `platform` = 'windows'
  AND `channel` = 'stable';

INSERT INTO `sys_app_version` (
  `id`, `version`, `channel`, `platform`, `release_notes`, `download_url`,
  `package_sha256`, `package_file_name`, `package_size`,
  `force_update`, `min_supported_version`, `status`,
  `published_at`, `published_by`, `created_by`, `updated_by`,
  `create_time`, `update_time`, `deleted`
) VALUES (
  4800000000000000002,
  '1.0.1',
  'stable',
  'windows',
  'LinkX 1.0.1: LinkMate Agent, Design Tokens, Electron 43 perf, optional message encryption',
  'releases/2026/08/29/LinkX-Installer-1.0.1.exe',
  '7510e1a18fc271a8af88fcb4488c9d4bcba00fd333ac2925d4fa16b9bda54f5a',
  'LinkX-Installer-1.0.1.exe',
  282628716,
  0,
  '',
  'published',
  NOW(),
  1,
  1,
  1,
  NOW(),
  NOW(),
  0
);

UPDATE `sys_runtime_setting`
SET `app_version` = '1.0.1',
    `app_channel` = 'stable',
    `release_notes` = 'LinkX 1.0.1: LinkMate Agent, Design Tokens, Electron 43 perf, optional message encryption',
    `download_url` = 'releases/2026/08/29/LinkX-Installer-1.0.1.exe',
    `force_update` = 0,
    `min_supported_version` = '',
    `update_time` = NOW()
WHERE `id` = 1;

COMMIT;
