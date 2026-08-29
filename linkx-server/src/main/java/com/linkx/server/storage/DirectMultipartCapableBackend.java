package com.linkx.server.storage;


/**
 * 作者：yangleduo
 */
import java.util.List;

/**
 * 支持 S3 原生分片直传的后端（MinIO / Cloudflare R2）。
 */
public interface DirectMultipartCapableBackend extends ObjectStorageBackend {

    String beginNativeMultipartUpload(String objectKey, String contentType) throws Exception;

    String presignNativeUploadPart(String objectKey, String uploadId, int partNumber) throws Exception;

    void completeNativeMultipartUpload(
            String objectKey, String uploadId, List<S3NativeMultipartSupport.UploadedPart> parts) throws Exception;
}
