package com.linkx.server.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("FileExtensionValidator 内容签名测试")
class FileExtensionValidatorTest {

    @Test
    void txtPlainText_isAccepted() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "新建 文本文档.txt", "text/plain", "你好世界".getBytes());
        assertTrue(FileExtensionValidator.hasSafeContentSignature(file));
    }

    @Test
    void mdPlainText_isAccepted() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "readme.md", "text/markdown", "# title\n".getBytes());
        assertTrue(FileExtensionValidator.hasSafeContentSignature(file));
    }

    @Test
    void txtDisguisedAsExe_isRejected() {
        byte[] mz = new byte[]{0x4D, 0x5A, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file", "evil.txt", "text/plain", mz);
        assertFalse(FileExtensionValidator.hasSafeContentSignature(file));
    }

    @Test
    void pdfMagic_isAccepted() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.pdf", "application/pdf", "%PDF-1.4".getBytes());
        assertTrue(FileExtensionValidator.hasSafeContentSignature(file));
    }

    @Test
    void unknownBinaryForPdf_isRejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.pdf", "application/pdf", new byte[]{0x01, 0x02, 0x03, 0x04});
        assertFalse(FileExtensionValidator.hasSafeContentSignature(file));
    }

    @Test
    void oleOfficeHeader_isAccepted() {
        byte[] ole = new byte[]{
                (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
        };
        MockMultipartFile file = new MockMultipartFile(
                "file", "old.xls", "application/vnd.ms-excel", ole);
        assertTrue(FileExtensionValidator.hasSafeContentSignature(file));
    }
}
