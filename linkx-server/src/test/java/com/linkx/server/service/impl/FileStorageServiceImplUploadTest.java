package com.linkx.server.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.service.FileStorageService;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.StatObjectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FileStorageServiceImpl 上传与 MinIO 成功路径")
class FileStorageServiceImplUploadTest {

    private static final String OBJECT_KEY = "2026/04/02/uuid.png";
    private static final String UPLOAD_ID = "upload-session-1";
    private static final String HASH = "a".repeat(64);

    @Mock MinioClient minioClient;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;
    @Mock HashOperations<String, Object, Object> hashOps;
    @Mock ExecutorService cleanupExecutor;
    @Mock StatObjectResponse statObjectResponse;
    @Mock ObjectWriteResponse objectWriteResponse;
    @Mock GetObjectResponse getObjectResponse;

    private FileStorageServiceImpl storage;
    private LinkxProperties props;

    @BeforeEach
    void setUp() throws Exception {
        props = new LinkxProperties();
        props.getMinio().setEndpoint("http://127.0.0.1:9000");
        props.getMinio().setBucketName("linkx-test");
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForHash()).thenReturn(hashOps);
        storage = new FileStorageServiceImpl(
                minioClient, props, redis, new ObjectMapper(), cleanupExecutor
        );
    }

    private static byte[] pngBytes() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01
        };
    }

    @Nested
    @DisplayName("uploadFile")
    class UploadFile {
        @Test
        @DisplayName("合法 PNG 上传成功并写入哈希")
        void success() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file", "photo.png", "image/png", pngBytes());
            when(minioClient.putObject(any())).thenReturn(objectWriteResponse);

            String key = storage.uploadFile(file, "my-photo");

            assertNotNull(key);
            assertTrue(key.endsWith(".png"));
            assertTrue(key.contains("/"));
            verify(minioClient).putObject(any());
            verify(valueOps).set(startsWith("linkx:filehash:"), eq(key), any());
        }
    }

    @Nested
    @DisplayName("deleteFile / deleteFileAsync")
    class Delete {
        @Test
        @DisplayName("deleteFile 成功删除对象")
        void deleteFileSuccess() throws Exception {
            storage.deleteFile(OBJECT_KEY);
            verify(minioClient).removeObject(any());
        }

        @Test
        @DisplayName("deleteFileAsync 成功删除对象")
        void deleteFileAsyncSuccess() throws Exception {
            storage.deleteFileAsync(OBJECT_KEY);
            verify(minioClient).removeObject(any());
        }
    }

    @Nested
    @DisplayName("getPresignedUrl")
    class Presign {
        @Test
        @DisplayName("MinIO 签名成功")
        void success() throws Exception {
            when(minioClient.getPresignedObjectUrl(any())).thenReturn("http://127.0.0.1:9000/linkx-test/signed?X-Amz=1");

            String url = storage.getPresignedUrl(OBJECT_KEY, 600);

            assertNotNull(url);
            assertTrue(url.contains("signed"));
            verify(minioClient).getPresignedObjectUrl(any());
        }
    }

    @Nested
    @DisplayName("openObject")
    class Open {
        @Test
        @DisplayName("打开对象成功")
        void success() throws Exception {
            byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
            when(minioClient.statObject(any())).thenReturn(statObjectResponse);
            when(statObjectResponse.contentType()).thenReturn("image/png");
            when(statObjectResponse.size()).thenReturn((long) body.length);
            when(minioClient.getObject(any())).thenReturn(getObjectResponse);

            FileStorageService.StoredObject obj = storage.openObject(OBJECT_KEY);

            assertNotNull(obj);
            assertEquals("image/png", obj.contentType());
            assertEquals(body.length, obj.size());
            assertEquals(OBJECT_KEY, obj.objectKey());
            assertSame(getObjectResponse, obj.stream());
        }
    }

    @Nested
    @DisplayName("objectExists / 内容哈希")
    class ExistsAndHash {
        @Test
        @DisplayName("objectExists 对象存在")
        void objectExistsTrue() throws Exception {
            when(minioClient.statObject(any())).thenReturn(statObjectResponse);
            assertTrue(storage.objectExists(OBJECT_KEY));
        }

        @Test
        @DisplayName("existsByContentHash Redis 命中且对象存在")
        void existsByContentHashTrue() throws Exception {
            when(valueOps.get("linkx:filehash:" + HASH)).thenReturn(OBJECT_KEY);
            when(minioClient.statObject(any())).thenReturn(statObjectResponse);

            assertTrue(storage.existsByContentHash(HASH));
        }

        @Test
        @DisplayName("getObjectKeyByHashInternal Redis 命中")
        void hashLookupHit() throws Exception {
            when(valueOps.get("linkx:filehash:" + HASH)).thenReturn(OBJECT_KEY);
            when(minioClient.statObject(any())).thenReturn(statObjectResponse);

            assertEquals(OBJECT_KEY, storage.getObjectKeyByHashInternal(HASH));
        }

        @Test
        @DisplayName("deleteFileAsync MinIO 异常吞掉")
        void deleteFileAsyncSwallowsError() throws Exception {
            doThrow(new RuntimeException("minio down")).when(minioClient).removeObject(any());
            assertDoesNotThrow(() -> storage.deleteFileAsync("2026/04/02/u.jpg"));
        }

        @Test
        @DisplayName("objectExists 对象不存在")
        void objectExistsFalse() throws Exception {
            doThrow(new RuntimeException("missing")).when(minioClient).statObject(any());
            assertFalse(storage.objectExists(OBJECT_KEY));
        }
    }

    @Nested
    @DisplayName("分片上传")
    class Multipart {
        @BeforeEach
        void multipartMeta() {
            when(valueOps.get(startsWith("linkx:mp:meta:"))).thenReturn(
                    "{\"objectName\":\"" + OBJECT_KEY + "\",\"contentType\":\"image/png\"}"
            );
        }

        @Test
        @DisplayName("uploadPart 已上传分片返回缓存 etag")
        void uploadPartCachedEtag() throws Exception {
            when(hashOps.get(startsWith("linkx:mp:parts:"), eq("1"))).thenReturn("cached-etag");
            String etag = storage.uploadPart(
                    OBJECT_KEY, UPLOAD_ID, 1,
                    new ByteArrayInputStream(new byte[]{1}), 1);
            assertEquals("cached-etag", etag);
            verify(minioClient, never()).putObject(any());
        }

        @Test
        @DisplayName("uploadPart 成功")
        void uploadPartSuccess() throws Exception {
            when(hashOps.get(startsWith("linkx:mp:parts:"), eq("1"))).thenReturn(null);
            when(minioClient.putObject(any())).thenReturn(objectWriteResponse);
            when(objectWriteResponse.etag()).thenReturn("\"etag-part-1\"");

            String etag = storage.uploadPart(
                    OBJECT_KEY, UPLOAD_ID, 1,
                    new ByteArrayInputStream(new byte[]{1, 2, 3}), 3);

            assertEquals("etag-part-1", etag);
            verify(minioClient).putObject(any());
            verify(hashOps).put(startsWith("linkx:mp:parts:"), eq("1"), eq("etag-part-1"));
        }

        @Test
        @DisplayName("completeMultipartUpload 单片合并成功")
        void completeSuccess() throws Exception {
            when(hashOps.entries(startsWith("linkx:mp:parts:"))).thenReturn(Map.of("1", "etag-1"));
            when(minioClient.composeObject(any())).thenReturn(objectWriteResponse);

            String result = storage.completeMultipartUpload(OBJECT_KEY, UPLOAD_ID, List.of());

            assertEquals(OBJECT_KEY, result);
            verify(minioClient).composeObject(any());
            verify(redis).delete(startsWith("linkx:mp:meta:"));
            verify(redis).delete(startsWith("linkx:mp:parts:"));
        }

        @Test
        @DisplayName("abortMultipartUpload 成功清理")
        void abortSuccess() throws Exception {
            when(hashOps.entries(startsWith("linkx:mp:parts:"))).thenReturn(Map.of("1", "etag-1"));
            doNothing().when(minioClient).removeObject(any());

            storage.abortMultipartUpload(OBJECT_KEY, UPLOAD_ID);

            verify(redis).delete(startsWith("linkx:mp:meta:"));
            verify(redis).delete(startsWith("linkx:mp:parts:"));
            verify(minioClient).removeObject(any());
        }
    }

    @Nested
    @DisplayName("copyObject")
    class Copy {
        @Test
        @DisplayName("复制对象成功")
        void success() throws Exception {
            String sourceKey = "2026/04/02/source.jpg";
            when(minioClient.statObject(any())).thenReturn(statObjectResponse);
            when(minioClient.copyObject(any())).thenReturn(objectWriteResponse);

            String dest = storage.copyObject(sourceKey, "copy.jpg");

            assertNotNull(dest);
            assertTrue(dest.endsWith(".jpg"));
            verify(minioClient).copyObject(any());
        }
    }
}
