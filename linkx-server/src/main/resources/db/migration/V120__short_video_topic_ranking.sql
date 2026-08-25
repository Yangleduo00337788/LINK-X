-- 短视频话题：置顶、热度衰减、管理端配置

ALTER TABLE `short_video_topic`
  ADD COLUMN `pinned` tinyint NOT NULL DEFAULT 0 COMMENT '管理端置顶' AFTER `post_count`,
  ADD COLUMN `pin_order` int NOT NULL DEFAULT 0 COMMENT '置顶排序，越大越靠前' AFTER `pinned`,
  ADD COLUMN `status` tinyint NOT NULL DEFAULT 1 COMMENT '1展示 0隐藏' AFTER `pin_order`,
  ADD COLUMN `display_name` varchar(64) DEFAULT NULL COMMENT '展示名（可选）' AFTER `status`,
  ADD COLUMN `hot_score` decimal(12,4) NOT NULL DEFAULT 0.0000 COMMENT '热度分（时间衰减）' AFTER `display_name`,
  ADD COLUMN `last_post_at` datetime DEFAULT NULL COMMENT '最近关联作品时间' AFTER `hot_score`;

CREATE INDEX `idx_short_video_topic_rank` ON `short_video_topic` (`pinned`, `pin_order`, `hot_score`);

UPDATE `short_video_topic` t
SET `last_post_at` = (
  SELECT MAX(p.create_time)
  FROM `short_video_post_topic` pt
  INNER JOIN `short_video_post` p ON p.id = pt.post_id AND p.deleted = 0
  WHERE pt.topic_id = t.id
);

UPDATE `short_video_topic`
SET `hot_score` = CASE
  WHEN `post_count` <= 0 THEN 0
  WHEN `last_post_at` IS NULL THEN ROUND(`post_count` * 0.1, 4)
  ELSE ROUND(
    `post_count` / (1 + TIMESTAMPDIFF(HOUR, `last_post_at`, NOW()) / 24.0),
    4
  )
END;

INSERT IGNORE INTO `sys_permission`
(`id`,`permission_code`,`permission_name`,`resource_type`,`resource_path`,`description`,`status`) VALUES
(2288,'admin:short-video:topic:edit','管理短视频话题','button',NULL,'置顶/隐藏/编辑话题',1);

INSERT IGNORE INTO `sys_role_permission` (`role_id`,`permission_id`,`create_by`) VALUES
(1001,2288,NULL),
(1004,2288,NULL);
