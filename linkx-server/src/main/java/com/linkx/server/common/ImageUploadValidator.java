package com.linkx.server.common;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * 上传图片的基础校验：Content-Type + 文件头 magic bytes。
 */
public final class ImageUploadValidator {

    private ImageUploadValidator() {
    }

    public static void assertSupportedImage(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("只支持图片文件");
        }
        if (!hasSupportedImageSignature(file)) {
            throw new IllegalArgumentException("无效的图片文件");
        }
    }

    private static boolean hasSupportedImageSignature(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            if (header.length < 3) {
                return false;
            }
            if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8 && header[2] == (byte) 0xFF) {
                return true;
            }
            if (header.length >= 8
                    && header[0] == (byte) 0x89
                    && header[1] == 0x50
                    && header[2] == 0x4E
                    && header[3] == 0x47) {
                return true;
            }
            if (header.length >= 6
                    && header[0] == 0x47
                    && header[1] == 0x49
                    && header[2] == 0x46
                    && header[3] == 0x38) {
                return true;
            }
            if (header.length >= 12
                    && header[0] == 0x52
                    && header[1] == 0x49
                    && header[2] == 0x46
                    && header[3] == 0x46
                    && header[8] == 0x57
                    && header[9] == 0x45
                    && header[10] == 0x42
                    && header[11] == 0x50) {
                return true;
            }
            return false;
        } catch (IOException e) {
            return false;
        }
    }
}
