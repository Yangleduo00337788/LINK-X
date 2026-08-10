-- 作者：yangleduo
-- =============================================================================
-- V53: 首页运营编排区块表
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_homepage_section` (
  `id`           bigint       NOT NULL COMMENT '雪花ID',
  `section_type` varchar(32)  NOT NULL COMMENT 'banner/recommend/activity/notice',
  `section_key`  varchar(64)  NOT NULL COMMENT '子键：home/discover/...',
  `title`        varchar(128) NOT NULL COMMENT '展示名称',
  `enabled`      tinyint(1)   NOT NULL DEFAULT 1 COMMENT '是否启用',
  `sort_order`   int          NOT NULL DEFAULT 0 COMMENT '排序，越小越靠前',
  `create_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted`      tinyint      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_homepage_section` (`section_type`, `section_key`, `deleted`),
  KEY `idx_homepage_section_sort` (`enabled`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页运营编排区块';

INSERT IGNORE INTO `sys_homepage_section`
(`id`, `section_type`, `section_key`, `title`, `enabled`, `sort_order`) VALUES
(5301, 'banner',    'home',          '首页 Banner',       1, 10),
(5302, 'recommend', 'discover',      '发现页推荐位',      1, 20),
(5303, 'recommend', 'chat_sidebar',  '聊天侧栏推荐',      1, 30),
(5304, 'recommend', 'moments',       '朋友圈推荐',        1, 40),
(5305, 'activity',  'default',       '活动专区',          1, 50),
(5306, 'notice',    'pinned',        '置顶公告',          1, 60);

INSERT IGNORE INTO `sys_admin_menu`
(`id`,`parent_id`,`name`,`title`,`path`,`component`,`icon`,`menu_type`,`permission_code`,`sort_order`,`hidden`,`cacheable`,`external_link`,`keep_alive`,`status`) VALUES
(46, 0, 'homepage-orchestration', '首页编排', '/admin/homepage-orchestration', 'views/HomepageOrchestrationView', 'Layout', 'menu', 'admin:homepage:list', 8, 0, 1, 0, 1, 1);

INSERT IGNORE INTO `sys_permission`
(`id`, `permission_code`, `permission_name`, `resource_type`, `resource_path`, `description`, `status`) VALUES
(2222, 'admin:homepage:list', '查看首页编排', 'page', '/admin/homepage-orchestration', '首页运营编排', 1),
(2223, 'admin:homepage:edit', '编辑首页编排', 'button', NULL, '调整区块排序与启用', 1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`, `create_by`) VALUES
(1001, 2222, NULL), (1001, 2223, NULL),
(1003, 2222, NULL), (1003, 2223, NULL);

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 46), (1003, 46);
