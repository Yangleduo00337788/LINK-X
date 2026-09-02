package com.linkx.server.common.admin;


/**
 * 作者：yangleduo
 */
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 CSV 导出对公式注入（CWE-1236）的中和与既有引用转义不回归。
 * 以 = + - @ 及制表/回车开头的单元格必须被前缀单引号，防止 Excel/DDE 执行。
 */
class AdminCsvResponsesTest {

    @Test
    void toBytes_neutralizesFormulaPrefixInjection() {
        assertPrefixed("=HYPERLINK(\"http://evil\")\"\"");
        assertPrefixed("+cmd|'/c calc'!A1");
        assertPrefixed("-2+3+cmd|'/c whoami'!A1");
        assertPrefixed("@SUM(1,1)");
        assertPrefixed("\t=1+2");
        assertPrefixed("\rCMD");
    }

    @Test
    void toBytes_prefixIsAppliedWithinQuotedCell() {
        // 含逗号的注入值仍会被正确引用且前缀在引号内生效
        byte[] out = AdminCsvResponses.toBytes(List.of("a", "b"), List.<String[]>of(new String[]{"=1,x", "ok"}));
        String csv = new String(out, StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFFa,b\n\"'=1,x\",ok\n"), "实际输出: " + csv);
    }

    @Test
    void toBytes_plainValueUnchanged() {
        byte[] out = AdminCsvResponses.toBytes(List.of("h"), List.<String[]>of(new String[]{"hello"}));
        String csv = new String(out, StandardCharsets.UTF_8);
        assertTrue(csv.endsWith("h\nhello\n"));
        assertFalse(csv.contains("'hello"));
    }

    @Test
    void toBytes_keepsNormalQuoting() {
        byte[] out = AdminCsvResponses.toBytes(List.of("h"), List.<String[]>of(new String[]{"a,\"b\"\nc"}));
        String csv = new String(out, StandardCharsets.UTF_8);
        assertTrue(csv.endsWith("h\n\"a,\"\"b\"\"\nc\"\n"), "实际输出: " + csv);
    }

    private static void assertPrefixed(String value) {
        byte[] out = AdminCsvResponses.toBytes(List.of("h"), List.<String[]>of(new String[]{value}));
        String csv = new String(out, StandardCharsets.UTF_8);
        // 该行单元格必须以 ' 开头（前一个字符为行首或逗号）
        String contentLine = csv.substring(csv.indexOf('\n') + 1);
        assertTrue(contentLine.startsWith("'") || contentLine.startsWith("\"'"),
                "未对公式前缀进行中和，实际输出行: " + contentLine);
    }
}