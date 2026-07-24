package com.linkx.server.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SensitiveDataMasker 脱敏")
class SensitiveDataMaskerTest {

    @Test
    void maskEmail_hidesLocalPart() {
        assertEquals("a***e@example.com", SensitiveDataMasker.maskEmail("alice@example.com"));
        assertEquals("a*@x.com", SensitiveDataMasker.maskEmail("ab@x.com"));
        assertEquals("*@x.com", SensitiveDataMasker.maskEmail("a@x.com"));
    }

    @Test
    void maskPhone_hidesMiddle() {
        assertEquals("138****8000", SensitiveDataMasker.maskPhone("13812348000"));
        assertEquals("138****8000", SensitiveDataMasker.maskPhone("+86-138-1234-8000"));
    }

    @Test
    void maskIp_hidesTail() {
        assertEquals("192.168.*.*", SensitiveDataMasker.maskIp("192.168.1.100"));
        assertTrue(SensitiveDataMasker.maskIp("2001:db8::1").contains("*"));
    }

    @Test
    void maskCode_hidesPrefix() {
        assertEquals("****56", SensitiveDataMasker.maskCode("123456"));
        assertEquals("**", SensitiveDataMasker.maskCode("12"));
    }
}
