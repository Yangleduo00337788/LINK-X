-- 短视频 P2：管理端菜单/权限 + 转码状态字段

ALTER TABLE `short_video_post`
  ADD COLUMN `transcode_status` varchar(16) NOT NULL DEFAULT 'skipped' COMMENT '转码状态 skipped/pending/processing/completed/failed' AFTER `share_count`,
  ADD COLUMN `transcoded_video_key` varchar(500) DEFAULT NULL COMMENT '转码后视频 object key' AFTER `transcode_status`;

INSERT IGNORE INTO `sys_admin_menu`
(`id`,`parent_id`,`name`,`title`,`path`,`component`,`icon`,`menu_type`,`permission_code`,`sort_order`,`hidden`,`cacheable`,`external_link`,`keep_alive`,`status`) VALUES
(112, 13, 'short-video', '短视频管理', '/admin/short-videos', 'views/ShortVideoListView', 'Videocam', 'menu', 'admin:short-video:list', 5, 0, 1, 0, 1, 1);

INSERT IGNORE INTO `sys_admin_role_menu` (`role_id`, `menu_id`) VALUES
(1001, 112),
(1004, 112);

INSERT IGNORE INTO `sys_permission`
(`id`,`permission_code`,`permission_name`,`resource_type`,`resource_path`,`description`,`status`) VALUES
(2285,'admin:short-video:list','查看短视频列表','page','/admin/short-videos','短视频作品/评论管理',1),
(2286,'admin:short-video:view','预览短视频内容','button',NULL,'预览视频/封面',1),
(2287,'admin:short-video:delete','下架短视频','button',NULL,'删除作品或评论',1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`,`permission_id`,`create_by`) VALUES
(1001,2285,NULL),(1001,2286,NULL),(1001,2287,NULL),
(1004,2285,NULL),(1004,2286,NULL),(1004,2287,NULL);
