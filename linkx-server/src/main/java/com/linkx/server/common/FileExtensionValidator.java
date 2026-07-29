package com.linkx.server.common;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;

/**
 * 云盘文件上传的安全校验：扩展名白名单 + 危险扩展名黑名单 + magic bytes 基础检查。
 * <p>
 * 可执行文件、脚本、网页文件等禁止上传；
 * 图片类文件（用户头像等）通过专用 {@link ImageUploadValidator} 做更严格的内容校验。
 * </p>
 */
public final class FileExtensionValidator {

    private FileExtensionValidator() {
    }

    private static final Set<String> BLOCKED_EXTENSIONS = Set.of(
            "exe", "msi", "dll", "so", "dylib",
            "bat", "cmd", "ps1", "vbs", "sh",
            "php", "phtml", "asp", "aspx", "jsp", "jspx",
            "html", "htm", "xhtml", "svg",
            "jar", "war", "ear",
            "js", "mjs", "ts", "jsx", "tsx",
            "css", "scss", "less",
            "py", "rb", "pl", "cgi",
            "sql", "db", "sqlite", "mdb",
            "reg", "inf", "ini", "cfg", "conf",
            "lnk", "pif", "application", "gadget",
            "msc", "ws", "wsf", "wsh"
    );

    /**
     * 允许上传的扩展名集合（兜底），空表示只靠黑名单拦截。
     * 设为 null 表示不限制（仅靠黑名单）；业务若需要严格白名单模式可在配置中覆盖。
     */
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            // 图片（移除 svg，防存储型 XSS；头像走 ImageUploadValidator 二次校验）
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "tiff", "tif", "heic", "heif", "avif",
            // 音视频
            "mp4", "mov", "avi", "mkv", "webm", "flv", "wmv", "m4v", "mp3", "wav", "ogg", "aac", "flac", "wma", "m4a",
            // 文档
            "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "md", "csv", "rtf", "odt", "ods", "odp",
            // 压缩包
            "zip", "rar", "7z", "tar", "gz", "bz2", "xz",
            // 设计稿 / CAD
            "psd", "ai", "eps", "sketch", "fig", "xd",
            // 字体
            "ttf", "otf", "woff", "woff2", "eot",
            // 其他常见格式
            "json", "xml", "yaml", "yml", "toml",
            "log", "srt", "vtt", "ass",
            "apk", "ipa"
    );

    /**
     * 校验文件扩展名。抛出 {@link IllegalArgumentException} 表示拒绝。
     *
     * @param file 上传的文件
     * @throws IllegalArgumentException 扩展名在黑名单或不在白名单中
     */
    public static void assertAllowedExtension(MultipartFile file) {
        String original = file.getOriginalFilename();
        String ext = extOf(original);

        if (ext.isEmpty()) {
            throw new IllegalArgumentException("文件缺少扩展名");
        }

        String lower = ext.toLowerCase(Locale.ROOT);

        if (BLOCKED_EXTENSIONS.contains(lower)) {
            throw new IllegalArgumentException(
                    String.format("文件类型 .%s 不允许上传（安全策略）", lower));
        }

        if (!ALLOWED_EXTENSIONS.contains(lower)) {
            throw new IllegalArgumentException(
                    String.format("文件类型 .%s 不在允许列表中，如需上传请联系管理员", lower));
        }
    }

    /**
     * 仅做 magic bytes 检查（不校验扩展名），用于已通过扩展名校验后的内容安全兜底。
     *
     * @param file 上传的文件
     * @return true 看起来是正常文件，false 可能是伪装文件
     */
    public static boolean hasSafeContentSignature(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(16);
            if (header.length < 2) return true;

            // PNG
            if (header[0] == (byte) 0x89 && header[1] == 0x50 && header[2] == 0x4E && header[3] == 0x47) {
                return true;
            }
            // JPEG
            if (header[0] == (byte) 0xFF && header[1] == (byte) 0xD8) {
                return true;
            }
            // GIF
            if (header[0] == 0x47 && header[1] == 0x49 && header[2] == 0x46) {
                return true;
            }
            // WebP
            if (header.length >= 12
                    && header[0] == 0x52 && header[1] == 0x49 && header[2] == 0x46 && header[3] == 0x46
                    && header[8] == 0x57 && header[9] == 0x45 && header[10] == 0x42 && header[11] == 0x50) {
                return true;
            }
            // ZIP / Office / APK (PK header)
            if (header[0] == 0x50 && header[1] == 0x4B) {
                return true;
            }
            // PDF
            if (header.length >= 4 && header[0] == 0x25 && header[1] == 0x50 && header[2] == 0x44 && header[3] == 0x46) {
                return true;
            }
            // 7z
            if (header.length >= 6 && header[0] == 0x37 && header[1] == 0x7A && header[2] == (byte) 0xBC && header[3] == (byte) 0xAF && header[4] == 0x27 && header[5] == 0x1C) {
                return true;
            }
            // MP4 / M4A / MOV (ftyp box)
            if (header.length >= 8 && header[4] == 0x66 && header[5] == 0x74 && header[6] == 0x79 && header[7] == 0x70) {
                return true;
            }
            // FLAC
            if (header.length >= 4 && header[0] == 0x66 && header[1] == 0x4C && header[2] == 0x61 && header[3] == 0x43) {
                return true;
            }

            // 未知签名默认 return false（fail-safe）：避免伪装扩展名的可执行/脚本文件绕过校验
            return false;
        } catch (IOException e) {
            return false;
        }
    }

    private static String extOf(String name) {
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i + 1).trim() : "";
    }
}
