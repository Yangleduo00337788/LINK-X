package com.linkx.server.security.crypto;


/**
 * 作者：yangleduo
 */
import com.linkx.server.entity.ImMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageContentCipherTest {

    private MessageContentCipher cipher;

    @BeforeEach
    void setUp() {
        cipher = new MessageContentCipher(MessageEncryptionTestSupport.activeOnlyProvider());
    }

    @Test
    void encryptAndDecrypt_roundTrip() {
        String plain = "你好 LinkX 消息加密测试";
        String stored = cipher.encryptPlaintextForStorage(plain);
        assertTrue(stored.startsWith(MessageContentCipher.PREFIX + "default:"));

        ImMessage message = ImMessage.builder()
                .content(stored)
                .contentEncVersion(MessageContentCipher.ENC_VERSION)
                .build();
        cipher.decryptMessageFields(message);
        assertEquals(plain, message.getContent());
    }

    @Test
    void encryptPlaintextForStorage_skipsWhenAlreadyEncrypted() {
        String stored = cipher.encryptPlaintextForStorage("hello");
        assertEquals(stored, cipher.encryptPlaintextForStorage(stored));
    }

    @Test
    void needsContentReencrypt_detectsPlaintextOnly() {
        assertTrue(cipher.needsContentReencrypt("明文消息", (byte) 0));
        assertFalse(cipher.needsContentReencrypt("lxenc:v1:default:abc", MessageContentCipher.ENC_VERSION));
    }

    @Test
    void encryptMessageFields_writesEncVersion() {
        ImMessage message = ImMessage.builder()
                .content("secret")
                .quoteContent("quote")
                .build();
        cipher.encryptMessageFields(message);
        assertEquals(MessageContentCipher.ENC_VERSION, message.getContentEncVersion());
        assertEquals(MessageContentCipher.ENC_VERSION, message.getQuoteContentEncVersion());
        assertNotEquals("secret", message.getContent());
    }

    @Test
    void keyRotation_reencryptsWithCurrentKeyId() {
        MessageKekProvider legacyProvider = legacyDefaultProvider();
        MessageContentCipher legacyCipher = new MessageContentCipher(legacyProvider);
        String legacyStored = legacyCipher.encryptPlaintextForStorage("rotate-me");

        MessageContentCipher rotationCipher = new MessageContentCipher(
                MessageEncryptionTestSupport.rotationProvider());
        assertTrue(rotationCipher.needsKeyRotation(legacyStored));
        assertTrue(legacyStored.startsWith(MessageContentCipher.PREFIX + "default:"));

        String rotated = rotationCipher.rotateCiphertextForStorage(legacyStored);
        assertTrue(rotated.startsWith(MessageContentCipher.PREFIX + "v2:"));
        assertFalse(rotationCipher.needsKeyRotation(rotated));

        ImMessage message = ImMessage.builder()
                .content(rotated)
                .contentEncVersion(MessageContentCipher.ENC_VERSION)
                .build();
        rotationCipher.decryptMessageFields(message);
        assertEquals("rotate-me", message.getContent());
    }

    private static MessageKekProvider legacyDefaultProvider() {
        com.linkx.server.config.LinkxProperties props = new com.linkx.server.config.LinkxProperties();
        props.getMessageEncryption().setEnabled(true);
        props.getMessageEncryption().setKek(MessageEncryptionTestSupport.LEGACY_KEK);
        props.getMessageEncryption().setKeyId("default");
        MessageKekProvider provider = new MessageKekProvider(props, new com.fasterxml.jackson.databind.ObjectMapper());
        provider.reloadKeyCache();
        return provider;
    }
}
