package com.linkx.server.common;


/**
 * 作者：yangleduo
 */
import com.linkx.server.service.FileStorageService.StoredObject;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
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
        MediaType mediaType = resolveMediaType(object);
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
            "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp",
            "image/apng", "image/avif", "image/bmp",
            "video/mp4", "video/webm", "video/quicktime"
    );

    public static ResponseEntity<InputStreamResource> inline(StoredObject object, String fileName) {
        return inline(object, fileName, null);
    }

    public static ResponseEntity<InputStreamResource> inline(
            StoredObject object, String fileName, String rangeHeader) {
        String name = (fileName == null || fileName.isBlank()) ? "file" : fileName.trim();
        String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
        MediaType mediaType = resolveMediaType(object);
        boolean isSafeInline = SAFE_INLINE_TYPES.contains(mediaType.toString().toLowerCase());
        String dispositionType = isSafeInline ? "inline" : "attachment";
        String contentDisposition = dispositionType + "; filename*=UTF-8''" + encoded;

        long totalSize = object.size();
        long[] range = parseByteRange(rangeHeader, totalSize);
        if (range != null) {
            long start = range[0];
            long end = range[1];
            long contentLength = end - start + 1;
            InputStream rangedStream;
            try {
                rangedStream = new BoundedInputStream(object.stream(), start, contentLength);
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                        .header(HttpHeaders.CONTENT_RANGE, "bytes */" + totalSize)
                        .build();
            }
            InputStreamResource body = new InputStreamResource(rangedStream) {
                @Override
                public long contentLength() {
                    return contentLength;
                }
            };
            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + totalSize)
                    .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                    .contentLength(contentLength)
                    .body(body);
        }

        InputStreamResource body = new InputStreamResource(object.stream()) {
            @Override
            public long contentLength() {
                return totalSize >= 0 ? totalSize : -1;
            }
        };
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60");
        if (totalSize >= 0) {
            builder.header(HttpHeaders.ACCEPT_RANGES, "bytes");
        }
        return builder.body(body);
    }

    private static MediaType resolveMediaType(StoredObject object) {
        try {
            return MediaType.parseMediaType(object.contentType());
        } catch (Exception e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private static long[] parseByteRange(String rangeHeader, long totalSize) {
        if (rangeHeader == null || rangeHeader.isBlank() || totalSize <= 0) {
            return null;
        }
        String trimmed = rangeHeader.trim();
        if (!trimmed.regionMatches(true, 0, "bytes=", 0, 6)) {
            return null;
        }
        String spec = trimmed.substring(6).trim();
        int dash = spec.indexOf('-');
        if (dash < 0) {
            return null;
        }
        String startPart = spec.substring(0, dash).trim();
        String endPart = spec.substring(dash + 1).trim();
        long start;
        long end;
        try {
            if (startPart.isEmpty()) {
                long suffix = Long.parseLong(endPart);
                if (suffix <= 0) {
                    return null;
                }
                start = Math.max(0, totalSize - suffix);
                end = totalSize - 1;
            } else {
                start = Long.parseLong(startPart);
                end = endPart.isEmpty() ? totalSize - 1 : Long.parseLong(endPart);
            }
        } catch (NumberFormatException e) {
            return null;
        }
        if (start < 0 || start >= totalSize || end < start) {
            return null;
        }
        end = Math.min(end, totalSize - 1);
        return new long[]{start, end};
    }

    private static void skipFully(InputStream in, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = in.skip(remaining);
            if (skipped > 0) {
                remaining -= skipped;
                continue;
            }
            if (in.read() == -1) {
                throw new EOFException("unexpected end of stream");
            }
            remaining--;
        }
    }

    private static final class BoundedInputStream extends InputStream {
        private final InputStream delegate;
        private long remaining;

        private BoundedInputStream(InputStream delegate, long skip, long length) throws IOException {
            this.delegate = delegate;
            skipFully(delegate, skip);
            this.remaining = length;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int value = delegate.read();
            if (value >= 0) {
                remaining--;
            }
            return value;
        }

        @Override
        public int read(byte[] buffer, int offset, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int toRead = (int) Math.min(len, remaining);
            int read = delegate.read(buffer, offset, toRead);
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }
    }
}
