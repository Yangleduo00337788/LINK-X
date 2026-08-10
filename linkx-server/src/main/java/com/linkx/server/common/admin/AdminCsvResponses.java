package com.linkx.server.common.admin;


/**
 * 作者：yangleduo
 */
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 管理端 CSV 导出响应（UTF-8 BOM，Excel 可直接打开）。
 */
public final class AdminCsvResponses {

    private static final byte[] UTF8_BOM = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private AdminCsvResponses() {
    }

    public static ResponseEntity<byte[]> csv(String filenamePrefix, List<String> headers, List<String[]> rows) {
        byte[] out = toBytes(headers, rows);
        String filename = buildFilename(filenamePrefix);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(out);
    }

    /** UTF-8 BOM + CSV 正文，供同步响应与异步落库共用。 */
    public static byte[] toBytes(List<String> headers, List<String[]> rows) {
        StringBuilder sb = new StringBuilder();
        if (headers != null) {
            appendRow(sb, headers.toArray(new String[0]));
        }
        if (rows != null) {
            for (String[] row : rows) {
                appendRow(sb, row);
            }
        }
        byte[] bodyBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[UTF8_BOM.length + bodyBytes.length];
        System.arraycopy(UTF8_BOM, 0, out, 0, UTF8_BOM.length);
        System.arraycopy(bodyBytes, 0, out, UTF8_BOM.length, bodyBytes.length);
        return out;
    }

    public static String buildFilename(String filenamePrefix) {
        String prefix = (filenamePrefix == null || filenamePrefix.isBlank()) ? "export" : filenamePrefix.trim();
        return prefix + "_" + LocalDateTime.now().format(TS) + ".csv";
    }

    private static void appendRow(StringBuilder sb, String[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(cells[i]));
        }
        sb.append('\n');
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needQuote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
        String v = value.replace("\"", "\"\"");
        return needQuote ? "\"" + v + "\"" : v;
    }

    public static String cell(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value);
    }
}
