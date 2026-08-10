package com.linkx.server.common;


/**
 * 作者：yangleduo
 */
import com.linkx.server.service.FileStorageService.StoredObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * 将 MinIO 对象流包装为 HTTP 下载响应。
 */
public final class MediaStreamResponses {

    private MediaStreamResponses() {
    }

    public static ResponseEntity<InputStreamResource> download(StoredObject object, String fileName) {
        String name = (fileName == null || fileName.isBlank()) ? "download" : fileName.trim();
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(object.contentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        InputStreamResource body = new InputStreamResource(object.stream()) {
            @Override
            public long contentLength() {
                return object.size() >= 0 ? object.size() : -1;
            }
        };
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(body);
    }

    private static final Set<String> SAFE_INLINE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "image/apng", "image/avif", "image/bmp"
    );

    public static ResponseEntity<InputStreamResource> inline(StoredObject object, String fileName) {
        String name = (fileName == null || fileName.isBlank()) ? "file" : fileName.trim();
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(object.contentType());
        } catch (Exception e) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        InputStreamResource body = new InputStreamResource(object.stream()) {
            @Override
            public long contentLength() {
                return object.size() >= 0 ? object.size() : -1;
            }
        };

        boolean isSafeImage = SAFE_INLINE_TYPES.contains(mediaType.toString().toLowerCase());
        String dispositionType = isSafeImage ? "inline" : "attachment";
        String contentDisposition = dispositionType + "; filename*=UTF-8''" + encoded;

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                .body(body);
    }
}
