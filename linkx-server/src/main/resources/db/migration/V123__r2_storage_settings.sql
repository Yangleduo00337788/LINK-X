-- Cloudflare R2 对象存储配置列
ALTER TABLE `sys_runtime_setting`
    ADD COLUMN r2_endpoint VARCHAR(256) DEFAULT NULL COMMENT 'R2 S3 API Endpoint' AFTER cos_cname_domain,
    ADD COLUMN r2_bucket_name VARCHAR(128) DEFAULT NULL COMMENT 'R2 桶名' AFTER r2_endpoint,
    ADD COLUMN r2_access_key_id VARCHAR(128) DEFAULT NULL COMMENT 'R2 Access Key ID' AFTER r2_bucket_name,
    ADD COLUMN r2_secret_access_key VARCHAR(256) DEFAULT NULL COMMENT 'R2 Secret Access Key' AFTER r2_access_key_id,
    ADD COLUMN r2_cname_domain VARCHAR(256) DEFAULT NULL COMMENT 'R2 自定义公开域名' AFTER r2_secret_access_key;
