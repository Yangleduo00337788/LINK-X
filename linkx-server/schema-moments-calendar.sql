-- ========================================
-- LinkX 友链和日历功能数据库表
-- 执行方式: mysql -u root -p linkx < schema-moments-calendar.sql
-- ========================================

-- 创建友链动态表
CREATE TABLE IF NOT EXISTS `moments_post` (
    `id` BIGINT NOT NULL COMMENT '动态ID(雪花算法)',
    `user_id` BIGINT NOT NULL COMMENT '发布者用户ID',
    `content` TEXT COMMENT '动态文字内容',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除:0未删除,1已删除',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_deleted_create` (`deleted`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='友链动态表';

-- 创建友链图片表
CREATE TABLE IF NOT EXISTS `moments_image` (
    `id` BIGINT NOT NULL COMMENT '图片ID(雪花算法)',
    `post_id` BIGINT NOT NULL COMMENT '所属动态ID',
    `url` VARCHAR(500) NOT NULL COMMENT '图片URL',
    `sort_order` INT DEFAULT 0 COMMENT '图片排序序号',
    PRIMARY KEY (`id`),
    INDEX `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='友链动态图片表';

-- 创建友链点赞表
CREATE TABLE IF NOT EXISTS `moments_like` (
    `id` BIGINT NOT NULL COMMENT '点赞ID(雪花算法)',
    `post_id` BIGINT NOT NULL COMMENT '点赞的动态ID',
    `user_id` BIGINT NOT NULL COMMENT '点赞用户ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除:0未删除,1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_user` (`post_id`, `user_id`, `deleted`),
    INDEX `idx_post_id` (`post_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='友链动态点赞表';

-- 创建友链评论表
CREATE TABLE IF NOT EXISTS `moments_comment` (
    `id` BIGINT NOT NULL COMMENT '评论ID(雪花算法)',
    `post_id` BIGINT NOT NULL COMMENT '所属动态ID',
    `user_id` BIGINT NOT NULL COMMENT '评论用户ID',
    `content` TEXT NOT NULL COMMENT '评论内容',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父评论ID(用于回复)',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除:0未删除,1已删除',
    PRIMARY KEY (`id`),
    INDEX `idx_post_id` (`post_id`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='友链动态评论表';

-- 创建日历事件表
CREATE TABLE IF NOT EXISTS `calendar_event` (
    `id` BIGINT NOT NULL COMMENT '事件ID(雪花算法)',
    `user_id` BIGINT NOT NULL COMMENT '所属用户ID',
    `title` VARCHAR(200) NOT NULL COMMENT '事件标题',
    `date` VARCHAR(10) NOT NULL COMMENT '事件日期(YYYY-MM-DD)',
    `time` VARCHAR(5) DEFAULT NULL COMMENT '事件时间(HH:mm)',
    `color` VARCHAR(50) DEFAULT NULL COMMENT '事件颜色',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除:0未删除,1已删除',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_user_date` (`user_id`, `date`),
    INDEX `idx_deleted_date` (`deleted`, `date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='日历事件表';
