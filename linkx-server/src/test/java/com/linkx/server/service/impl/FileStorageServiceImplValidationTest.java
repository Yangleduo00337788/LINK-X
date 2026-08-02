package com.linkx.server.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.config.LinkxProperties;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FileStorageServiceImpl 校验与路径")
class FileStorageServiceImplValidationTest {

    @Mock MinioClient minioClient;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;
    @Mock HashOperations<String, Object, Object> hashOps;
    @Mock ExecutorService cleanupExecutor;

    private FileStorageServiceImpl storage;
    private LinkxProperties props;

    @BeforeEach
    void setUp() {
        props = new LinkxProperties();
        props.getMinio().setEndpoint("http://127.0.0.1:9000");
        props.getMinio().setBucketName("linkx-test");
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForHash()).thenReturn(hashOps);
        storage = new FileStorageServiceImpl(
                minioClient, props, redis, new ObjectMapper(), cleanupExecutor
        );
    }

    @Nested
    @DisplayName("deleteFileAsync / deleteFile")
    class Delete {
        @Test
        @DisplayName("deleteFileAsync 空 key 直接返回")
        void deleteFileAsyncBlank() {
            storage.deleteFileAsync(null);
            storage.deleteFileAsync("  ");
            verifyNoInteractions(minioClient);
        }

        @Test
        @DisplayName("deleteFile 空与非法路径")
        void deleteFileGuards() {
            storage.deleteFile(null);
            storage.deleteFile("");
            storage.deleteFile("http://127.0.0.1:9000/linkx-test/../secret");
            verifyNoInteractions(minioClient);
        }
    }

    @Nested
    @DisplayName("getPresignedUrl")
    class Presign {
        @Test
        @DisplayName("null/blank 返回 null")
        void nullOrBlank() {
            assertNull(storage.getPresignedUrl(null, 3600));
            assertNull(storage.getPresignedUrl("", 3600));
        }

        @Test
        @DisplayName("data:/blob: 前缀原样返回")
        void passthroughSchemes() {
            assertEquals("/static/x", storage.getPresignedUrl("/static/x", 60));
            assertEquals("data:image/png;base64,abc", storage.getPresignedUrl("data:image/png;base64,abc", 60));
            assertEquals("blob:uuid", storage.getPresignedUrl("blob:uuid", 60));
        }

        @Test
        @DisplayName("非法 key 拒绝签名")
        void illegalKey() {
            assertNull(storage.getPresignedUrl("a/../b.png", 60));
            assertNull(storage.getPresignedUrl("http://evil.com/x", 60));
            verifyNoInteractions(minioClient);
        }
    }

    @Nested
    @DisplayName("initiateMultipartUpload")
    class MultipartInit {
        @Test
        @DisplayName("非法对象名")
        void badObjectName() {
            assertThrows(IllegalArgumentException.class,
                    () -> storage.initiateMultipartUpload("../x.jpg", "image/jpeg"));
            assertThrows(IllegalArgumentException.class,
                    () -> storage.initiateMultipartUpload("/abs.jpg", "image/jpeg"));
            assertThrows(IllegalArgumentException.class,
                    () -> storage.initiateMultipartUpload("", "image/jpeg"));
        }

        @Test
        @DisplayName("不允许的 Content-Type")
        void badContentType() {
            assertThrows(IllegalArgumentException.class,
                    () -> storage.initiateMultipartUpload("2026/04/02/u.jpg", "application/x-msdownload"));
        }

        @Test
        @DisplayName("不允许的扩展名")
        void badExtension() {
            assertThrows(IllegalArgumentException.class,
                    () -> storage.initiateMultipartUpload("2026/04/02/u.exe", "application/octet-stream"));
        }

        @Test
        @DisplayName("合法参数写入 Redis 会话")
        void ok() throws Exception {
            when(valueOps.get(anyString())).thenReturn(null);
            var session = storage.initiateMultipartUpload("2026/04/02/uuid.jpg", "image/jpeg; charset=utf-8");
            assertNotNull(session.uploadId());
            assertEquals("2026/04/02/uuid.jpg", session.objectName());
            verify(valueOps).set(startsWith("linkx:mp:meta:"), anyString(), any());
        }
    }

    @Nested
    @DisplayName("objectExists / hash / copy / open")
    class OtherGuards {
        @Test
        @DisplayName("objectExists 空或非法")
        void objectExists() {
            assertFalse(storage.objectExists(null));
            assertFalse(storage.objectExists("../x"));
        }

        @Test
        @DisplayName("existsByContentHash 格式校验")
        void hashExists() {
            assertFalse(storage.existsByContentHash("not-a-hash"));
            assertFalse(storage.existsByContentHash(null));
        }

        @Test
        @DisplayName("getObjectKeyByHashInternal 非法 hash")
        void hashLookup() {
            assertNull(storage.getObjectKeyByHashInternal("zz"));
        }

        @Test
        @DisplayName("saveContentHash 空参数跳过")
        void saveHashNoop() {
            storage.saveContentHash("", "k");
            storage.saveContentHash("a".repeat(64), "");
            verify(valueOps, never()).set(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("copyObject 源 key 校验")
        void copyGuards() {
            assertThrows(IllegalArgumentException.class, () -> storage.copyObject("", "f.jpg"));
            assertThrows(IllegalArgumentException.class, () -> storage.copyObject("../x", "f.jpg"));
        }

        @Test
        @DisplayName("openObject 空与非法 key")
        void openGuards() {
            assertThrows(IllegalArgumentException.class, () -> storage.openObject(null));
            assertThrows(IllegalArgumentException.class, () -> storage.openObject("/abs"));
            assertThrows(IllegalArgumentException.class, () -> storage.openObject("a/../b"));
        }

        @Test
        @DisplayName("abortMultipartUpload 空 uploadId")
        void abortBlank() {
            storage.abortMultipartUpload("obj", "");
            storage.abortMultipartUpload("obj", null);
            verify(redis, never()).delete(anyString());
        }
    }

    @Test
    @DisplayName("extractObjectKey 多种 endpoint 前缀")
    void extractObjectKeyVariants() {
        assertEquals("d/e.png", storage.extractObjectKey("http://localhost:9000/linkx-test/d/e.png"));
        assertEquals("plain", storage.extractObjectKey("plain"));
    }

    @Nested
    @DisplayName("uploadFile 校验")
    class UploadValidation {
        @Test
        @DisplayName("空文件拒绝")
        void emptyFile() {
            MockMultipartFile empty = new MockMultipartFile("f", "a.txt", "text/plain", new byte[0]);
            assertThrows(IllegalArgumentException.class, () -> storage.uploadFile(empty, null));
        }

        @Test
        @DisplayName("超大文件拒绝")
        void tooLarge() {
            props.getMinio().setMaxFileSize(10);
            byte[] body = "01234567890".getBytes(StandardCharsets.UTF_8);
            MockMultipartFile big = new MockMultipartFile("f", "a.txt", "text/plain", body);
            assertThrows(IllegalArgumentException.class, () -> storage.uploadFile(big, null));
        }

        @Test
        @DisplayName("非法 MIME 拒绝")
        void badMime() {
            MockMultipartFile bad = new MockMultipartFile("f", "a.exe", "application/x-msdownload", "x".getBytes());
            assertThrows(IllegalArgumentException.class, () -> storage.uploadFile(bad, null));
        }
    }

    @Nested
    @DisplayName("分片扩展")
    class MultipartMore {
        @Test
        @DisplayName("image/jpg 归一化为 jpeg")
        void jpgContentType() throws Exception {
            when(valueOps.get(anyString())).thenReturn(null);
            var session = storage.initiateMultipartUpload("2026/04/02/u.jpg", "image/jpg");
            assertEquals("image/jpeg", session.contentType());
        }

        @Test
        @DisplayName("uploadPart 非法 partNumber")
        void badPartNumber() {
            assertThrows(IllegalArgumentException.class,
                    () -> storage.uploadPart("obj", "uid", 0, new ByteArrayInputStream(new byte[1]), 1));
        }

        @Test
        @DisplayName("uploadPart 会话不存在")
        void missingSession() {
            when(valueOps.get(startsWith("linkx:mp:meta:"))).thenReturn(null);
            assertThrows(IllegalArgumentException.class,
                    () -> storage.uploadPart("obj", "missing", 1, new ByteArrayInputStream(new byte[1]), 1));
        }

        @Test
        @DisplayName("listUploadedParts 解析 hash")
        void listParts() throws Exception {
            when(valueOps.get(startsWith("linkx:mp:meta:"))).thenReturn(
                    "{\"objectName\":\"2026/04/02/u.jpg\",\"contentType\":\"image/jpeg\"}"
            );
            when(hashOps.entries(startsWith("linkx:mp:parts:"))).thenReturn(
                    java.util.Map.of("1", "etag-1", "2", "etag-2")
            );
            var parts = storage.listUploadedParts("sess-1");
            assertEquals(2, parts.size());
            assertEquals(1, parts.get(0).partNumber());
        }

        @Test
        @DisplayName("completeMultipartUpload 分片序号不连续")
        void completeGapParts() throws Exception {
            when(valueOps.get(startsWith("linkx:mp:meta:"))).thenReturn(
                    "{\"objectName\":\"2026/04/02/u.jpg\",\"contentType\":\"image/jpeg\"}"
            );
            when(hashOps.entries(startsWith("linkx:mp:parts:"))).thenReturn(
                    java.util.Map.of("1", "etag-1", "3", "etag-3")
            );
            assertThrows(IllegalArgumentException.class,
                    () -> storage.completeMultipartUpload("2026/04/02/u.jpg", "sess-1", List.of()));
        }
    }

    @Nested
    @DisplayName("哈希与 copy")
    class HashAndCopy {
        @Test
        @DisplayName("saveContentHash 合法写入")
        void saveHashOk() {
            storage.saveContentHash("a".repeat(64), "2026/04/02/f.jpg");
            verify(valueOps).set(startsWith("linkx:filehash:"), eq("2026/04/02/f.jpg"), any());
        }

        @Test
        @DisplayName("existsByContentHash Redis 命中但对象缺失")
        void hashExists() throws Exception {
            String hash = "b".repeat(64);
            when(valueOps.get("linkx:filehash:" + hash)).thenReturn("2026/04/02/f.jpg");
            doThrow(new RuntimeException("missing")).when(minioClient).statObject(any());
            assertFalse(storage.existsByContentHash(hash));
        }

        @Test
        @DisplayName("copyObject 源不存在")
        void copyMissingSource() throws Exception {
            doThrow(new RuntimeException("missing")).when(minioClient).statObject(any());
            assertThrows(IllegalArgumentException.class,
                    () -> storage.copyObject("2026/04/02/missing.jpg", "f.jpg"));
        }
    }
}
