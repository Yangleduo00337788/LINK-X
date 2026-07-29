package com.linkx.server.common;

import com.linkx.server.service.FileStorageService.StoredObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MediaStreamResponses 测试")
class MediaStreamResponsesTest {

    private StoredObject createMockObject(String contentType, String fileName) {
        return new StoredObject(
                new ByteArrayInputStream(new byte[1024]),
                contentType,
                1024,
                fileName
        );
    }

    @Test
    @DisplayName("图片类型应返回 inline")
    void inline_imageType_shouldReturnInline() {
        StoredObject obj = createMockObject("image/png", "test.png");
        ResponseEntity<?> response = MediaStreamResponses.inline(obj, "test.png");

        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disposition.startsWith("inline;"), "图片类型应使用 inline: " + disposition);
    }

    @Test
    @DisplayName("JPEG 图片应返回 inline")
    void inline_jpeg_shouldReturnInline() {
        StoredObject obj = createMockObject("image/jpeg", "photo.jpg");
        ResponseEntity<?> response = MediaStreamResponses.inline(obj, "photo.jpg");

        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disposition.startsWith("inline;"), "JPEG 应使用 inline: " + disposition);
    }

    @Test
    @DisplayName("GIF 图片应返回 inline")
    void inline_gif_shouldReturnInline() {
        StoredObject obj = createMockObject("image/gif", "anim.gif");
        ResponseEntity<?> response = MediaStreamResponses.inline(obj, "anim.gif");

        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disposition.startsWith("inline;"), "GIF 应使用 inline: " + disposition);
    }

    @Test
    @DisplayName("WebP 图片应返回 inline")
    void inline_webp_shouldReturnInline() {
        StoredObject obj = createMockObject("image/webp", "image.webp");
        ResponseEntity<?> response = MediaStreamResponses.inline(obj, "image.webp");

        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disposition.startsWith("inline;"), "WebP 应使用 inline: " + disposition);
    }

    @Test
    @DisplayName("PDF 应返回 attachment（防XSS）")
    void inline_pdf_shouldReturnAttachment() {
        StoredObject obj = createMockObject("application/pdf", "doc.pdf");
        ResponseEntity<?> response = MediaStreamResponses.inline(obj, "doc.pdf");

        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disposition.startsWith("attachment;"), "PDF 应使用 attachment: " + disposition);
    }

    @Test
    @DisplayName("HTML 应返回 attachment（防XSS）")
    void inline_html_shouldReturnAttachment() {
        StoredObject obj = createMockObject("text/html", "page.html");
        ResponseEntity<?> response = MediaStreamResponses.inline(obj, "page.html");

        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disposition.startsWith("attachment;"), "HTML 应使用 attachment: " + disposition);
    }

    @Test
    @DisplayName("JavaScript 应返回 attachment（防XSS）")
    void inline_javascript_shouldReturnAttachment() {
        StoredObject obj = createMockObject("application/javascript", "app.js");
        ResponseEntity<?> response = MediaStreamResponses.inline(obj, "app.js");

        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disposition.startsWith("attachment;"), "JS 应使用 attachment: " + disposition);
    }

    @Test
    @DisplayName("SVG 应返回 attachment（防XSS）")
    void inline_svg_shouldReturnAttachment() {
        StoredObject obj = createMockObject("image/svg+xml", "vector.svg");
        ResponseEntity<?> response = MediaStreamResponses.inline(obj, "vector.svg");

        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disposition.startsWith("attachment;"), "SVG 应使用 attachment: " + disposition);
    }

    @Test
    @DisplayName("XML 应返回 attachment（防XSS）")
    void inline_xml_shouldReturnAttachment() {
        StoredObject obj = createMockObject("application/xml", "data.xml");
        ResponseEntity<?> response = MediaStreamResponses.inline(obj, "data.xml");

        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disposition.startsWith("attachment;"), "XML 应使用 attachment: " + disposition);
    }

    @Test
    @DisplayName("未知类型应返回 attachment")
    void inline_unknownType_shouldReturnAttachment() {
        StoredObject obj = createMockObject("application/octet-stream", "file.bin");
        ResponseEntity<?> response = MediaStreamResponses.inline(obj, "file.bin");

        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disposition.startsWith("attachment;"), "未知类型应使用 attachment: " + disposition);
    }

    @Test
    @DisplayName("download 方法应始终返回 attachment")
    void download_alwaysReturnsAttachment() {
        StoredObject obj = createMockObject("image/png", "test.png");
        ResponseEntity<?> response = MediaStreamResponses.download(obj, "test.png");

        String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
        assertTrue(disposition.startsWith("attachment;"), "download 应使用 attachment: " + disposition);
    }
}
