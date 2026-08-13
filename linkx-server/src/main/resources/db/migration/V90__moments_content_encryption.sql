-- V90: 朋友圈正文/位置落库加密版本标记（密文前缀 lxenc:v1:，与 IM 消息共用 MESSAGE_KEK）

ALTER TABLE `moments_post`
    ADD COLUMN `content_enc_version` TINYINT NOT NULL DEFAULT 0
        COMMENT '0=明文(历史) 1=lxenc:v1' AFTER `content`,
    ADD COLUMN `location_enc_version` TINYINT NOT NULL DEFAULT 0
        COMMENT '0=明文 1=加密' AFTER `location`;

ALTER TABLE `moments_comment`
    ADD COLUMN `content_enc_version` TINYINT NOT NULL DEFAULT 0
        COMMENT '0=明文(历史) 1=lxenc:v1' AFTER `content`;
