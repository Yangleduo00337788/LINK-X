package com.linkx.server.storage;


/**
 * 作者：yangleduo
 */
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import io.minio.CreateMultipartUploadResponse;
import io.minio.MinioAsyncClient;
import io.minio.messages.Part;

import java.util.Comparator;
import java.util.List;

/**
 * 暴露 MinIO SDK 受保护的 S3 原生分片 API（MinIO / R2）。
 */
public final class ExtendedMinioAsyncClient extends MinioAsyncClient {

    private ExtendedMinioAsyncClient(MinioAsyncClient client) {
        super(client);
    }

    public static ExtendedMinioAsyncClient wrap(MinioAsyncClient client) {
        return new ExtendedMinioAsyncClient(client);
    }

    public String createMultipartUpload(String bucket, String objectKey, String contentType) throws Exception {
        Multimap<String, String> headers = HashMultimap.create();
        if (contentType != null && !contentType.isBlank()) {
            headers.put("Content-Type", contentType);
        }
        CreateMultipartUploadResponse response =
                super.createMultipartUpload(bucket, null, objectKey, headers, null);
        return response.result().uploadId();
    }

    public void completeMultipartUpload(
            String bucket, String objectKey, String uploadId, List<S3NativeMultipartSupport.UploadedPart> parts)
            throws Exception {
        Part[] partArray = parts.stream()
                .sorted(Comparator.comparingInt(S3NativeMultipartSupport.UploadedPart::partNumber))
                .map(p -> new Part(p.partNumber(), p.etag()))
                .toArray(Part[]::new);
        super.completeMultipartUpload(bucket, null, objectKey, uploadId, partArray, null, null);
    }
}
