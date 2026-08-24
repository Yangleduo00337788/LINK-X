-- 对象存储：腾讯云 COS
ALTER TABLE sys_runtime_setting
    ADD COLUMN cos_region VARCHAR(64) DEFAULT NULL COMMENT 'COS 地域 ap-beijing' AFTER local_storage_path,
    ADD COLUMN cos_bucket_name VARCHAR(128) DEFAULT NULL COMMENT 'COS 桶名' AFTER cos_region,
    ADD COLUMN cos_secret_id VARCHAR(256) DEFAULT NULL COMMENT 'COS SecretId' AFTER cos_bucket_name,
    ADD COLUMN cos_secret_key VARCHAR(512) DEFAULT NULL COMMENT 'COS SecretKey' AFTER cos_secret_id,
    ADD COLUMN cos_cname_domain VARCHAR(512) DEFAULT NULL COMMENT 'COS CNAME 域名' AFTER cos_secret_key;
