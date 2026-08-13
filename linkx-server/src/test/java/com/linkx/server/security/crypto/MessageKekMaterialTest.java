package com.linkx.server.security.crypto;


/**
 * 作者：yangleduo
 */
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MessageKekMaterialTest {

    @Test
    void toAesKeyBytes_acceptsUtf8SecretAtLeast32Chars() {
        byte[] key = MessageKekMaterial.toAesKeyBytes("linkx-test-kek-32-chars-minimum!!");
        assertEquals(32, key.length);
    }

    @Test
    void toAesKeyBytes_acceptsBase64Secret() {
        byte[] raw = new byte[32];
        for (int i = 0; i < raw.length; i++) {
            raw[i] = (byte) i;
        }
        String base64 = Base64.getEncoder().encodeToString(raw);
        assertArrayEquals(raw, MessageKekMaterial.toAesKeyBytes(base64));
    }

    @Test
    void toAesKeyBytes_rejectsTooShortSecret() {
        assertThrows(IllegalStateException.class,
                () -> MessageKekMaterial.toAesKeyBytes("too-short"));
    }
}
