package com.linkx.server.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.config.LinkxProperties;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileStorageServiceImpl key/路径工具")
class FileStorageServiceImplKeyTest {

    @Mock MinioClient minioClient;
    @Mock StringRedisTemplate redis;
    @Mock ExecutorService cleanupExecutor;

    private FileStorageServiceImpl storage;

    @BeforeEach
    void setUp() {
        LinkxProperties props = new LinkxProperties();
        props.getMinio().setEndpoint("http://127.0.0.1:9000");
        props.getMinio().setBucketName("linkx-test");
        storage = new FileStorageServiceImpl(
                minioClient,
                props,
                redis,
                new ObjectMapper(),
                cleanupExecutor
        );
    }

    @Test
    @DisplayName("extractObjectKey 剥离 query 与 endpoint 前缀")
    void extractObjectKey_stripsPrefix() {
        assertEquals("a/b.png", storage.extractObjectKey("http://127.0.0.1:9000/linkx-test/a/b.png?X-Amz=1"));
        assertEquals("plain-key", storage.extractObjectKey("plain-key"));
        assertNull(storage.extractObjectKey(null));
    }

    @Test
    @DisplayName("allocateObjectName 合法扩展名")
    void allocateObjectName_ok() {
        String key = storage.allocateObjectName("photo.JPG");
        assertTrue(key.toLowerCase().endsWith(".jpg"));
        assertFalse(key.contains(".."));
    }

    @Test
    @DisplayName("allocateObjectName 非法扩展名")
    void allocateObjectName_badExt() {
        assertThrows(IllegalArgumentException.class, () -> storage.allocateObjectName("x.exe"));
    }
}
