package com.linkx.server.security.crypto;


/**
 * 作者：yangleduo
 */
import com.linkx.server.entity.ImMessage;
import com.linkx.server.entity.MomentsComment;
import com.linkx.server.entity.MomentsPost;
import com.linkx.server.util.ApiEncryptUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.function.Consumer;

/**
 * IM 消息与朋友圈文本字段落库加解密。
 * <p>
 * 密文格式：{@code lxenc:v1:{keyId}:{base64(iv+ciphertext+tag)}}
 * </p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MessageContentCipher {

    public static final String PREFIX = "lxenc:v1:";
    public static final byte ENC_VERSION = 1;

    private final MessageKekProvider kekProvider;

    public boolean isEnabled() {
        return kekProvider.isEncryptionEnabled();
    }

    public String currentKeyPrefix() {
        return PREFIX + kekProvider.currentKeyId() + ":";
    }

    public void encryptMessageFields(ImMessage message) {
        if (!isEnabled() || message == null) {
            return;
        }
        if (shouldEncryptValue(message.getContent())) {
            message.setContent(encryptPlaintext(message.getContent()));
            message.setContentEncVersion(ENC_VERSION);
        } else if (message.getContent() == null) {
            message.setContentEncVersion((byte) 0);
        }
        if (shouldEncryptValue(message.getQuoteContent())) {
            message.setQuoteContent(encryptPlaintext(message.getQuoteContent()));
            message.setQuoteContentEncVersion(ENC_VERSION);
        } else if (message.getQuoteContent() == null) {
            message.setQuoteContentEncVersion((byte) 0);
        }
    }

    public void decryptMessageFields(ImMessage message) {
        if (message == null) {
            return;
        }
        if (isEncryptedContent(message.getContent(), message.getContentEncVersion())) {
            message.setContent(decryptCiphertext(message.getContent()));
        }
        if (isEncryptedContent(message.getQuoteContent(), message.getQuoteContentEncVersion())) {
            message.setQuoteContent(decryptCiphertext(message.getQuoteContent()));
        }
    }

    public void decryptMessageFields(Iterable<ImMessage> messages) {
        if (messages == null) {
            return;
        }
        for (ImMessage message : messages) {
            decryptMessageFields(message);
        }
    }

    public void encryptMomentsPostFields(MomentsPost post) {
        if (!isEnabled() || post == null) {
            return;
        }
        encryptTextField(post.getContent(), post::setContent, post.getContentEncVersion(), post::setContentEncVersion);
        encryptTextField(post.getLocation(), post::setLocation, post.getLocationEncVersion(), post::setLocationEncVersion);
    }

    public void decryptMomentsPostFields(MomentsPost post) {
        if (post == null) {
            return;
        }
        decryptTextField(post.getContent(), post.getContentEncVersion(), post::setContent);
        decryptTextField(post.getLocation(), post.getLocationEncVersion(), post::setLocation);
    }

    public void decryptMomentsPostFields(Iterable<MomentsPost> posts) {
        if (posts == null) {
            return;
        }
        for (MomentsPost post : posts) {
            decryptMomentsPostFields(post);
        }
    }

    public void encryptMomentsCommentFields(MomentsComment comment) {
        if (!isEnabled() || comment == null) {
            return;
        }
        encryptTextField(comment.getContent(), comment::setContent,
                comment.getContentEncVersion(), comment::setContentEncVersion);
    }

    public void decryptMomentsCommentFields(MomentsComment comment) {
        if (comment == null) {
            return;
        }
        decryptTextField(comment.getContent(), comment.getContentEncVersion(), comment::setContent);
    }

    public void decryptMomentsCommentFields(Iterable<MomentsComment> comments) {
        if (comments == null) {
            return;
        }
        for (MomentsComment comment : comments) {
            decryptMomentsCommentFields(comment);
        }
    }

    public boolean needsLocationReencrypt(String stored, Byte version) {
        return needsContentReencrypt(stored, version);
    }

    public boolean isEncryptedContent(String stored, Byte version) {
        if (!StringUtils.hasText(stored)) {
            return false;
        }
        if (version != null && version == ENC_VERSION) {
            return true;
        }
        return stored.startsWith(PREFIX);
    }

    /** 明文是否需要重加密落库（版本为 0 且非空、且尚未带 lxenc 前缀）。 */
    public boolean needsContentReencrypt(String stored, Byte version) {
        return StringUtils.hasText(stored)
                && (version == null || version == 0)
                && !stored.startsWith(PREFIX);
    }

    public boolean needsQuoteReencrypt(String stored, Byte version) {
        return needsContentReencrypt(stored, version);
    }

    /** 密文是否使用非当前 keyId，需要轮换重加密。 */
    public boolean needsKeyRotation(String stored) {
        if (!isEnabled() || !StringUtils.hasText(stored) || !stored.startsWith(PREFIX)) {
            return false;
        }
        String keyId = extractKeyId(stored);
        return keyId != null && !kekProvider.currentKeyId().equals(keyId);
    }

    /** 将明文加密为落库格式；已加密则原样返回。 */
    public String encryptPlaintextForStorage(String plaintext) {
        if (!isEnabled() || !StringUtils.hasText(plaintext)) {
            return plaintext;
        }
        if (plaintext.startsWith(PREFIX)) {
            return plaintext;
        }
        return encryptPlaintext(plaintext);
    }

    /** 从落库格式解密为明文；非加密内容原样返回；密文损坏时返回空串避免接口失败。 */
    public String decryptTextFromStorage(String stored, Byte version) {
        if (!isEncryptedContent(stored, version)) {
            return stored;
        }
        try {
            return decryptCiphertext(stored);
        } catch (Exception ex) {
            log.warn("decrypt text from storage failed: {}", ex.getMessage());
            return "";
        }
    }

    /** 用旧 keyId 解密后用当前 keyId 重加密；无需轮换则原样返回。 */
    public String rotateCiphertextForStorage(String stored) {
        if (!needsKeyRotation(stored)) {
            return stored;
        }
        String plaintext = decryptCiphertext(stored);
        return encryptPlaintext(plaintext);
    }

    public String extractKeyId(String stored) {
        if (!StringUtils.hasText(stored) || !stored.startsWith(PREFIX)) {
            return null;
        }
        String rest = stored.substring(PREFIX.length());
        int colon = rest.indexOf(':');
        if (colon <= 0) {
            throw new IllegalStateException("invalid encrypted message format: missing keyId");
        }
        return rest.substring(0, colon);
    }

    private String encryptPlaintext(String plaintext) {
        byte[] key = kekProvider.resolveAesKey();
        String payload = ApiEncryptUtils.encryptUtf8ToBase64(key, plaintext);
        return PREFIX + kekProvider.currentKeyId() + ":" + payload;
    }

    private String decryptCiphertext(String stored) {
        if (!StringUtils.hasText(stored)) {
            return stored;
        }
        String payload = extractPayloadBase64(stored);
        String keyId = extractKeyId(stored);
        byte[] key = keyId != null
                ? kekProvider.resolveAesKeyByKeyId(keyId)
                : kekProvider.resolveAesKey();
        return ApiEncryptUtils.decryptUtf8FromBase64(key, payload);
    }

    private static String extractPayloadBase64(String stored) {
        if (!stored.startsWith(PREFIX)) {
            return stored;
        }
        int thirdColon = indexOfNth(stored, ':', 3);
        if (thirdColon < 0 || thirdColon >= stored.length() - 1) {
            throw new IllegalStateException("invalid encrypted message format");
        }
        return stored.substring(thirdColon + 1);
    }

    private void encryptTextField(String value, Consumer<String> valueSetter,
                                  Byte version, Consumer<Byte> versionSetter) {
        if (shouldEncryptValue(value)) {
            valueSetter.accept(encryptPlaintext(value));
            versionSetter.accept(ENC_VERSION);
        } else if (value == null) {
            versionSetter.accept((byte) 0);
        }
    }

    private void decryptTextField(String value, Byte version, Consumer<String> valueSetter) {
        if (isEncryptedContent(value, version)) {
            try {
                valueSetter.accept(decryptCiphertext(value));
            } catch (Exception ex) {
                log.warn("decrypt text field failed: {}", ex.getMessage());
                valueSetter.accept("");
            }
        }
    }

    private static boolean shouldEncryptValue(String value) {
        return StringUtils.hasText(value);
    }

    private static int indexOfNth(String text, char ch, int n) {
        int found = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ch) {
                found++;
                if (found == n) {
                    return i;
                }
            }
        }
        return -1;
    }
}
