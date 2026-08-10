-- 作者：yangleduo
-- 可在管理端热更新的运行时配置（覆盖 application.yml 中的 linkx.* 默认值）
CREATE TABLE IF NOT EXISTS `sys_runtime_setting` (
  `id`               bigint       NOT NULL COMMENT '固定单行，主键恒为 1',
  `captcha_enabled`  tinyint(1)   NOT NULL DEFAULT 1 COMMENT '是否启用图形验证码',
  `app_version`      varchar(32)  NOT NULL DEFAULT '1.0.0' COMMENT '应用版本号',
  `app_channel`      varchar(32)  NOT NULL DEFAULT 'stable' COMMENT '发布渠道',
  `release_notes`    varchar(2000) NULL COMMENT '更新说明',
  `download_url`     varchar(512)  NULL COMMENT '下载地址',
  `max_upload_bytes` bigint       NOT NULL DEFAULT 104857600 COMMENT '最大上传字节数',
  `update_by`        bigint        NULL COMMENT '最后修改人',
  `create_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统运行时配置';
