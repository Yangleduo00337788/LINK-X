-- V89: IM 消息内容落库加密版本标记（密文存于 content / quote_content 字段，前缀 lxenc:v1:）

ALTER TABLE `im_message`
    ADD COLUMN `content_enc_version` TINYINT NOT NULL DEFAULT 0
        COMMENT '0=明文(历史) 1=lxenc:v1' AFTER `content`,
    ADD COLUMN `quote_content_enc_version` TINYINT NOT NULL DEFAULT 0
        COMMENT '0=明文 1=加密' AFTER `quote_content`;
