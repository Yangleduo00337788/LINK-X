-- 对象存储热切换：MinIO / OSS / 本地
ALTER TABLE sys_runtime_setting
    ADD COLUMN storage_provider VARCHAR(16) DEFAULT 'minio' COMMENT 'minio|oss|local' AFTER max_upload_bytes,
    ADD COLUMN minio_endpoint VARCHAR(512) DEFAULT NULL COMMENT 'MinIO Endpoint' AFTER storage_provider,
    ADD COLUMN minio_bucket_name VARCHAR(128) DEFAULT NULL COMMENT 'MinIO 桶名' AFTER minio_endpoint,
    ADD COLUMN minio_access_key VARCHAR(256) DEFAULT NULL COMMENT 'MinIO Access Key' AFTER minio_bucket_name,
    ADD COLUMN minio_secret_key VARCHAR(512) DEFAULT NULL COMMENT 'MinIO Secret Key' AFTER minio_access_key,
    ADD COLUMN oss_endpoint VARCHAR(512) DEFAULT NULL COMMENT 'OSS Endpoint' AFTER minio_secret_key,
    ADD COLUMN oss_bucket_name VARCHAR(128) DEFAULT NULL COMMENT 'OSS 桶名' AFTER oss_endpoint,
    ADD COLUMN oss_access_key_id VARCHAR(256) DEFAULT NULL COMMENT 'OSS AccessKeyId' AFTER oss_bucket_name,
    ADD COLUMN oss_access_key_secret VARCHAR(512) DEFAULT NULL COMMENT 'OSS AccessKeySecret' AFTER oss_access_key_id,
    ADD COLUMN oss_cname_domain VARCHAR(512) DEFAULT NULL COMMENT 'OSS CNAME 域名' AFTER oss_access_key_secret,
    ADD COLUMN local_storage_path VARCHAR(512) DEFAULT NULL COMMENT '本地存储根目录' AFTER oss_cname_domain;
