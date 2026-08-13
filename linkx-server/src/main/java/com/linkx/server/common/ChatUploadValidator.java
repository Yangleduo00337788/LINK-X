package com.linkx.server.common;


/**
 * 作者：yangleduo
 */
import com.linkx.server.service.FileStorageService;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * 聊天附件上传校验：扩展名黑名单 + 图片 magic + 分片合并后文件头兜底。
 */
public final class ChatUploadValidator {

    private ChatUploadValidator() {
    }

    public static void assertBeforeUpload(MultipartFile file) {
        FileExtensionValidator.assertAllowedExtension(file);
        String contentType = file.getContentType();
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            ImageUploadValidator.assertSupportedImage(file);
        }
    }

    public static void assertComposedObject(FileStorageService fileStorageService, String objectKey,
                                            String fileName, String contentType) {
        String nameForCheck = fileName != null && !fileName.isBlank() ? fileName : objectKey;
        try (FileStorageService.StoredObject object = fileStorageService.openObject(objectKey)) {
            byte[] header = object.stream().readNBytes(16);
            FileExtensionValidator.assertHeaderSafe(header, nameForCheck);
            if (contentType != null && contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
                assertImageHeader(header);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("无法校验上传文件内容");
        } catch (Exception e) {
            throw new IllegalArgumentException("无法读取上传文件内容");
        }
    }

    private static void assertImageHeader(byte[] header) {
        if (!ImageUploadValidator.hasSupportedImageSignature(header)) {
            throw new IllegalArgumentException("无效的图片文件");
        }
    }
}
