package com.linkx.server.common;


/**
 * 作者：yangleduo
 */
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

/**
 * 客户端安装包上传校验（管理端版本发布专用）。
 */
public final class InstallerUploadValidator {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "exe", "msi", "dmg", "deb", "rpm", "appimage"
    );

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "application/octet-stream",
            "application/x-msdownload",
            "application/vnd.microsoft.portable-executable",
            "application/x-msi",
            "application/x-apple-diskimage",
            "application/x-debian-package",
            "application/vnd.debian.binary-package",
            "application/x-rpm",
            "application/zip"
    );

    private InstallerUploadValidator() {
    }

    public static void assertInstallerFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("安装包不能为空");
        }
        String ext = extOf(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("仅支持 .exe / .msi / .dmg / .deb / .rpm / .AppImage 安装包");
        }
        String contentType = normalizeContentType(file.getContentType());
        if (contentType != null && !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("不允许的安装包类型: " + file.getContentType());
        }
        if (!hasInstallerSignature(file, ext)) {
            throw new IllegalArgumentException("安装包内容与扩展名不匹配");
        }
    }

    private static boolean hasInstallerSignature(MultipartFile file, String ext) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(16);
            if (header.length < 2) {
                return false;
            }
            return switch (ext) {
                case "exe" -> header[0] == 0x4D && header[1] == 0x5A;
                case "msi" -> (header[0] == 0x4D && header[1] == 0x5A)
                        || (header[0] == 0x50 && header[1] == 0x4B);
                case "dmg" -> header.length >= 4
                        && ((header[0] == 0x78 && (header[1] == 0x01 || header[1] == (byte) 0x9C || header[1] == (byte) 0xDA))
                        || (header[0] == 0x1F && header[1] == (byte) 0x8B)
                        || (header[0] == 'k' && header[1] == 'o' && header[2] == 'l' && header[3] == 'x'));
                case "deb" -> header.length >= 8
                        && header[0] == 0x21 && header[1] == 0x3C
                        && header[2] == 0x61 && header[3] == 0x72
                        && header[4] == 0x63 && header[5] == 0x68 && header[6] == 0x3E;
                case "rpm" -> header.length >= 4
                        && (header[0] & 0xFF) == 0xED && (header[1] & 0xFF) == 0xAB
                        && (header[2] & 0xFF) == 0xEE && (header[3] & 0xFF) == 0xDB;
                case "appimage" -> header.length >= 4
                        && header[0] == 0x7F && header[1] == 0x45 && header[2] == 0x4C && header[3] == 0x46;
                default -> false;
            };
        } catch (IOException e) {
            return false;
        }
    }

    private static String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return null;
        }
        int semi = contentType.indexOf(';');
        return (semi >= 0 ? contentType.substring(0, semi) : contentType).trim().toLowerCase(Locale.ROOT);
    }

    private static String extOf(String name) {
        if (name == null) {
            return "";
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).trim() : "";
    }
}
