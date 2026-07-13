package com.linkx.server.service;

import com.linkx.server.config.LinkxProperties;
import com.linkx.server.service.impl.FileStorageServiceImpl;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 文件存储服务单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("文件存储服务测试")
class FileStorageServiceTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private LinkxProperties linkxProperties;

    @InjectMocks
    private FileStorageServiceImpl fileStorageService;

    private static final String MINIO_ENDPOINT = "http://localhost:9000";
    private static final String BUCKET_NAME = "linkx";
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB

    @BeforeEach
    void setUp() {
        setupMinioConfig();
    }

    @Test
    @DisplayName("上传文件 - 有效的图片文件应成功")
    void shouldUploadFileSuccessfully() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "avatar.png",
                "image/png",
                "test image content".getBytes()
        );

        // Act
        String resultUrl = fileStorageService.uploadFile(file);

        // Assert
        assertNotNull(resultUrl);
        assertTrue(resultUrl.startsWith(MINIO_ENDPOINT));
        assertTrue(resultUrl.contains(BUCKET_NAME));

        // Verify MinIO client was called
        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        
        PutObjectArgs capturedArgs = captor.getValue();
        assertEquals(BUCKET_NAME, capturedArgs.bucket());
        assertEquals("image/png", capturedArgs.contentType());
    }

    @Test
    @DisplayName("上传文件 - 空文件应抛出异常")
    void shouldThrowExceptionForEmptyFile() {
        // Arrange
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file",
                "empty.txt",
                "text/plain",
                new byte[0]
        );

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> fileStorageService.uploadFile(emptyFile));
        assertEquals("文件不能为空", exception.getMessage());
    }

    @Test
    @DisplayName("上传文件 - 超大文件应抛出异常")
    void shouldThrowExceptionForOversizedFile() {
        // Arrange
        byte[] largeContent = new byte[(int) (MAX_FILE_SIZE + 1)];
        MockMultipartFile largeFile = new MockMultipartFile(
                "file",
                "large.bin",
                "application/octet-stream",
                largeContent
        );

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> fileStorageService.uploadFile(largeFile));
        assertTrue(exception.getMessage().contains("超过限制"));
    }

    @Test
    @DisplayName("上传文件 - 自定义文件名应生效")
    void shouldUploadWithCustomFileName() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "original.png",
                "image/png",
                "test content".getBytes()
        );
        String customName = "custom/path/avatar.png";

        // Act
        String resultUrl = fileStorageService.uploadFile(file, customName);

        // Assert
        assertTrue(resultUrl.endsWith(customName));

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        assertTrue(captor.getValue().object().endsWith(customName));
    }

    @Test
    @DisplayName("删除文件 - 有效的文件 URL 应成功")
    void shouldDeleteFileSuccessfully() throws Exception {
        // Arrange
        String fileUrl = MINIO_ENDPOINT + "/" + BUCKET_NAME + "/avatar/123/test.png";

        // Act
        fileStorageService.deleteFile(fileUrl);

        // Assert
        ArgumentCaptor<RemoveObjectArgs> captor = ArgumentCaptor.forClass(RemoveObjectArgs.class);
        verify(minioClient).removeObject(captor.capture());
        
        RemoveObjectArgs capturedArgs = captor.getValue();
        assertEquals(BUCKET_NAME, capturedArgs.bucket());
        assertEquals("avatar/123/test.png", capturedArgs.object());
    }

    @Test
    @DisplayName("删除文件 - 空 URL 应忽略")
    void shouldIgnoreEmptyUrl() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            fileStorageService.deleteFile(null);
            fileStorageService.deleteFile("");
        });
        
        // Verify no MinIO calls were made
        verifyNoInteractions(minioClient);
    }

    @Test
    @DisplayName("删除文件 - 无效 URL 应忽略")
    void shouldIgnoreInvalidUrl() {
        // Arrange
        String invalidUrl = "http://other-server.com/bucket/file.png";

        // Act & Assert
        assertDoesNotThrow(() -> fileStorageService.deleteFile(invalidUrl));
        
        // Verify no MinIO calls were made
        verifyNoInteractions(minioClient);
    }

    @Test
    @DisplayName("上传头像 - 应使用头像专用路径")
    void shouldUploadAvatarWithDedicatedPath() throws Exception {
        // Arrange
        MockMultipartFile avatarFile = new MockMultipartFile(
                "file",
                "photo.jpg",
                "image/jpeg",
                "avatar image".getBytes()
        );
        Long userId = 12345L;
        String customName = "avatar/" + userId + "/1234567890.jpg";

        // Act
        String resultUrl = fileStorageService.uploadFile(avatarFile, customName);

        // Assert
        assertTrue(resultUrl.contains("avatar/" + userId + "/"));

        ArgumentCaptor<PutObjectArgs> captor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(captor.capture());
        PutObjectArgs capturedArgs = captor.getValue();
        assertNotNull(capturedArgs);
    }

    @Test
    @DisplayName("上传文件 - MinIO 异常应包装为 RuntimeException")
    void shouldWrapMinioException() throws Exception {
        // Arrange
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "test.png",
                "image/png",
                "content".getBytes()
        );

        // 创建注入后的新服务实例来测试异常处理
        FileStorageServiceImpl serviceWithException = new FileStorageServiceImpl(minioClient, linkxProperties);
        
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        // Act & Assert
        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> serviceWithException.uploadFile(file));
        assertTrue(exception.getMessage().contains("Connection refused"), 
            "异常消息应该包含原始错误: " + exception.getMessage());
    }

    private void setupMinioConfig() {
        LinkxProperties.Minio minioConfig = new LinkxProperties.Minio();
        minioConfig.setEndpoint(MINIO_ENDPOINT);
        minioConfig.setBucketName(BUCKET_NAME);
        minioConfig.setMaxFileSize(MAX_FILE_SIZE);
        when(linkxProperties.getMinio()).thenReturn(minioConfig);
    }

    @Test
    @DisplayName("获取预签名 URL - 应返回公开访问 URL")
    void shouldReturnPresignedUrl() {
        // Arrange
        String objectName = "2024/07/12/test-file.png";

        // Act
        String url = fileStorageService.getPresignedUrl(objectName, 3600);

        // Assert
        assertEquals(MINIO_ENDPOINT + "/" + BUCKET_NAME + "/" + objectName, url);
    }
}
