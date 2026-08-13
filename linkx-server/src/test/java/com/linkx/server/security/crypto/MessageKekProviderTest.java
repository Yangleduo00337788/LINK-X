package com.linkx.server.security.crypto;


/**
 * 作者：yangleduo
 */
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageKekProviderTest {

    @Test
    void loadsActiveAndLegacyKeys() {
        MessageKekProvider provider = MessageEncryptionTestSupport.rotationProvider();
        assertEquals("v2", provider.currentKeyId());
        assertTrue(provider.hasKeyId("v2"));
        assertTrue(provider.hasKeyId("default"));
        assertArrayEquals(provider.resolveAesKeyByKeyId("v2"), provider.resolveAesKey());
    }

    @Test
    void activeKeyOverridesLegacyMapWithSameId() {
        MessageKekProvider provider = MessageEncryptionTestSupport.rotationProvider();
        byte[] active = provider.resolveAesKeyByKeyId("v2");
        byte[] legacy = provider.resolveAesKeyByKeyId("default");
        assertFalseArraysEqual(active, legacy);
    }

    private static void assertFalseArraysEqual(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return;
        }
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                return;
            }
        }
        throw new AssertionError("expected different key material");
    }
}
