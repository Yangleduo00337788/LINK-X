-- =============================================================================
-- V48: 独立版本管理 CRUD + 发布流（恢复版本菜单）
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_app_version` (
  `id`               bigint       NOT NULL COMMENT '雪花ID',
  `version`          varchar(32)  NOT NULL COMMENT '版本号',
  `channel`          varchar(32)  NOT NULL DEFAULT 'stable' COMMENT '发布渠道 stable/beta/dev',
  `release_notes`    varchar(2000) NULL COMMENT '更新说明',
  `download_url`     varchar(512)  NULL COMMENT '下载地址',
  `force_update`     tinyint(1)   NOT NULL DEFAULT 0 COMMENT '有更新时强制升级',
  `min_supported_version` varchar(32) NULL COMMENT '最低支持版本',
  `status`           varchar(16)  NOT NULL DEFAULT 'draft' COMMENT 'draft/published/archived',
  `published_at`     datetime     NULL COMMENT '发布时间',
  `published_by`     bigint       NULL COMMENT '发布人',
  `created_by`       bigint       NULL,
  `updated_by`       bigint       NULL,
  `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`          tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `idx_app_version_status` (`status`),
  KEY `idx_app_version_channel` (`channel`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='应用版本发布记录';

-- 恢复版本管理菜单（V10 曾隐藏）
UPDATE `sys_admin_menu`
SET `hidden` = 0,
    `status` = 1,
    `deleted` = 0,
    `component` = 'views/VersionListView',
    `updated_at` = NOW()
WHERE `name` = 'versions'
   OR `path` = '/admin/versions';

INSERT IGNORE INTO `sys_permission`
(`id`, `permission_code`, `permission_name`, `resource_type`, `resource_path`, `description`, `status`) VALUES
(2208, 'admin:version:view',   '查看版本详情', 'button', NULL, '版本详情', 1),
(2209, 'admin:version:create', '新增版本',     'button', NULL, '新增版本', 1),
(2210, 'admin:version:edit',   '编辑版本',     'button', NULL, '编辑版本', 1),
(2211, 'admin:version:delete', '删除版本',     'button', NULL, '删除版本', 1),
(2212, 'admin:version:publish','发布版本',     'button', NULL, '发布版本', 1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `create_by`) VALUES
(1001, 2128, NULL), (1001, 2208, NULL), (1001, 2209, NULL), (1001, 2210, NULL),
(1001, 2211, NULL), (1001, 2212, NULL);

-- 将当前运行时配置导入为已发布版本（仅当表为空时）
INSERT INTO `sys_app_version`
(`id`, `version`, `channel`, `release_notes`, `download_url`, `force_update`, `min_supported_version`,
 `status`, `create_time`, `update_time`, `deleted`)
SELECT
  4800000000000000001,
  COALESCE(NULLIF(TRIM(`app_version`), ''), '1.0.0'),
  COALESCE(NULLIF(TRIM(`app_channel`), ''), 'stable'),
  `release_notes`,
  `download_url`,
  IFNULL(`force_update`, 0),
  `min_supported_version`,
  'published',
  NOW(),
  NOW(),
  0
FROM `sys_runtime_setting`
WHERE `id` = 1
  AND NOT EXISTS (SELECT 1 FROM `sys_app_version` WHERE `deleted` = 0 LIMIT 1);
