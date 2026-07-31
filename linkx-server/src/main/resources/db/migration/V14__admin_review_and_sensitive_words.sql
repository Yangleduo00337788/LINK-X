-- =============================================================================
-- V14: 内容审核任务表 + 审核中心/敏感词菜单与权限
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_review_task` (
  `id` BIGINT NOT NULL COMMENT '主键ID(雪花算法)',
  `source_type` VARCHAR(32) NOT NULL COMMENT '来源: report/sensitive/manual',
  `target_type` VARCHAR(32) DEFAULT NULL COMMENT '目标类型: group/user/moment/message/feedback',
  `target_id` VARCHAR(64) DEFAULT NULL COMMENT '目标业务ID',
  `reporter_user_id` BIGINT DEFAULT NULL COMMENT '举报人用户ID',
  `reporter_username` VARCHAR(64) DEFAULT NULL COMMENT '举报人用户名',
  `title` VARCHAR(128) DEFAULT NULL COMMENT '标题摘要',
  `content_snapshot` TEXT NOT NULL COMMENT '内容快照',
  `risk_level` VARCHAR(16) NOT NULL DEFAULT 'medium' COMMENT '风险等级: low/medium/high',
  `status` VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending/approved/rejected',
  `feedback_id` BIGINT DEFAULT NULL COMMENT '关联反馈ID',
  `assignee_id` BIGINT DEFAULT NULL COMMENT '处理人',
  `resolution` VARCHAR(1000) DEFAULT NULL COMMENT '处理意见',
  `resolved_by` BIGINT DEFAULT NULL COMMENT '完结操作人',
  `resolved_at` DATETIME DEFAULT NULL COMMENT '完结时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_feedback_id` (`feedback_id`),
  KEY `idx_review_status` (`status`),
  KEY `idx_review_source` (`source_type`),
  KEY `idx_review_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容审核任务';

-- 菜单：审核中心（目录）+ 违规内容 + 敏感词
INSERT IGNORE INTO `sys_admin_menu`
(`id`,`parent_id`,`name`,`title`,`path`,`component`,`icon`,`menu_type`,`permission_code`,`sort_order`,`hidden`,`cacheable`,`external_link`,`keep_alive`,`status`) VALUES
(13, 0,  'review',          '审核中心', '/admin/review',            NULL,                         'Shield',     'dir',  NULL,                        5, 0, 1, 0, 1, 1),
(14, 13, 'review-task',     '违规内容', '/admin/reviews',           'views/ReviewListView',        'Document',   'menu', 'admin:review:list',         1, 0, 1, 0, 1, 1),
(15, 13, 'sensitive-word',  '敏感词管理', '/admin/sensitive-words', 'views/SensitiveWordListView', 'Key',        'menu', 'admin:sensitive-word:list', 2, 0, 1, 0, 1, 1);

-- 反馈菜单排序挪到审核中心之后
UPDATE `sys_admin_menu` SET `sort_order` = 6 WHERE `name` = 'feedback';
UPDATE `sys_admin_menu` SET `sort_order` = 7 WHERE `name` = 'settings';

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 13),(1001, 14),(1001, 15);

INSERT IGNORE INTO `sys_permission`
(`id`,`permission_code`,`permission_name`,`resource_type`,`resource_path`,`description`,`status`) VALUES
(2129,'admin:review:list','查看审核列表','page','/admin/reviews','内容审核',1),
(2130,'admin:review:approve','审核通过','button',NULL,'审核通过',1),
(2131,'admin:review:reject','审核驳回','button',NULL,'审核驳回',1),
(2132,'admin:sensitive-word:list','查看敏感词','page','/admin/sensitive-words','敏感词管理',1),
(2133,'admin:sensitive-word:create','新增敏感词','button',NULL,'新增敏感词',1),
(2134,'admin:sensitive-word:edit','编辑敏感词','button',NULL,'编辑敏感词',1),
(2135,'admin:sensitive-word:delete','删除敏感词','button',NULL,'删除敏感词',1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`,`permission_id`,`create_by`) VALUES
(1001,2129,NULL),(1001,2130,NULL),(1001,2131,NULL),
(1001,2132,NULL),(1001,2133,NULL),(1001,2134,NULL),(1001,2135,NULL);
