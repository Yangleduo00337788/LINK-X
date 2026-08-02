package com.linkx.server.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ImageUploadValidator 图片上传校验")
class ImageUploadValidatorTest {

    @Test
    @DisplayName("JPEG magic 应通过")
    void jpeg_ok() {
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00, 0x01, 0x02};
        MockMultipartFile file = new MockMultipartFile("f", "a.jpg", "image/jpeg", jpeg);
        assertDoesNotThrow(() -> ImageUploadValidator.assertSupportedImage(file));
    }

    @Test
    @DisplayName("PNG magic 应通过")
    void png_ok() {
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("f", "a.png", "image/png", png);
        assertDoesNotThrow(() -> ImageUploadValidator.assertSupportedImage(file));
    }

    @Test
    @DisplayName("GIF magic 应通过")
    void gif_ok() {
        byte[] gif = new byte[] {0x47, 0x49, 0x46, 0x38, 0x39, 0x61, 0, 0, 0, 0, 0, 0};
        MockMultipartFile file = new MockMultipartFile("f", "a.gif", "image/gif", gif);
        assertDoesNotThrow(() -> ImageUploadValidator.assertSupportedImage(file));
    }

    @Test
    @DisplayName("WEBP magic 应通过")
    void webp_ok() {
        byte[] webp = new byte[] {
                0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50
        };
        MockMultipartFile file = new MockMultipartFile("f", "a.webp", "image/webp", webp);
        assertDoesNotThrow(() -> ImageUploadValidator.assertSupportedImage(file));
    }

    @Test
    @DisplayName("非 image Content-Type 应失败")
    void nonImageContentType_fails() {
        MockMultipartFile file = new MockMultipartFile("f", "a.bin", "application/octet-stream", new byte[] {1, 2, 3});
        assertThrows(IllegalArgumentException.class, () -> ImageUploadValidator.assertSupportedImage(file));
    }

    @Test
    @DisplayName("错误 magic 应失败")
    void badMagic_fails() {
        MockMultipartFile file = new MockMultipartFile("f", "a.jpg", "image/jpeg", new byte[] {1, 2, 3, 4, 5});
        assertThrows(IllegalArgumentException.class, () -> ImageUploadValidator.assertSupportedImage(file));
    }

    @Test
    @DisplayName("空 Content-Type 应失败")
    void nullContentType_fails() {
        MockMultipartFile file = new MockMultipartFile("f", "a.jpg", null, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF});
        assertThrows(IllegalArgumentException.class, () -> ImageUploadValidator.assertSupportedImage(file));
    }
}
