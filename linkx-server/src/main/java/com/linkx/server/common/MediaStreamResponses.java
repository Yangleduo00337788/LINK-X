package com.linkx.server.common;

import com.linkx.server.service.FileStorageService.StoredObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

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
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename*=UTF-8''" + encoded)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                .body(body);
    }
}
