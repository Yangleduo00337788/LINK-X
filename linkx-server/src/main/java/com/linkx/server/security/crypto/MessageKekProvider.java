package com.linkx.server.security.crypto;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.linkx.server.config.LinkxProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 消息落库加密主密钥（KEK）提供者，支持当前密钥 + 历史密钥（仅解密）。
 * <p>
 * 轮换流程：将旧 KEK 写入 {@code MESSAGE_KEK_LEGACY_MAP}，更新 {@code MESSAGE_KEK} 与
 * {@code MESSAGE_KEK_KEY_ID}，由 Snail Job {@code message_content_key_rotate} 批量重加密。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageKekProvider {

    private final LinkxProperties linkxProperties;
    private final ObjectMapper objectMapper;

    private volatile Map<String, byte[]> keyIdToBytes = Map.of();

    @PostConstruct
    void initKeys() {
        reloadKeyCache();
    }

    /** 重新加载当前与历史 KEK（启动时与单元测试使用）。 */
    void reloadKeyCache() {
        keyIdToBytes = Collections.unmodifiableMap(buildKeyMap());
        if (isEncryptionEnabled()) {
            log.info("[消息加密] 已加载密钥 keyIds={}", keyIdToBytes.keySet());
        }
    }

    public boolean isEncryptionEnabled() {
        return linkxProperties.getMessageEncryption().isEnabled();
    }

    public String currentKeyId() {
        String keyId = linkxProperties.getMessageEncryption().getKeyId();
        return StringUtils.hasText(keyId) ? keyId.trim() : "default";
    }

    public Set<String> loadedKeyIds() {
        return keyIdToBytes.keySet();
    }

    public byte[] resolveAesKey() {
        return resolveAesKeyByKeyId(currentKeyId());
    }

    public byte[] resolveAesKeyByKeyId(String keyId) {
        if (!StringUtils.hasText(keyId)) {
            throw new IllegalStateException("消息加密 keyId 不能为空");
        }
        byte[] key = keyIdToBytes.get(keyId.trim());
        if (key == null) {
            throw new IllegalStateException("未知消息加密 keyId: " + keyId);
        }
        return key;
    }

    public boolean hasKeyId(String keyId) {
        return StringUtils.hasText(keyId) && keyIdToBytes.containsKey(keyId.trim());
    }

    private Map<String, byte[]> buildKeyMap() {
        Map<String, byte[]> map = new LinkedHashMap<>();
        LinkxProperties.MessageEncryption config = linkxProperties.getMessageEncryption();
        if (config.isEnabled() && StringUtils.hasText(config.getKek())) {
            map.put(currentKeyId(), MessageKekMaterial.toAesKeyBytes(config.getKek()));
        }
        mergeLegacyKeys(map, config.getLegacyKekMap());
        return map;
    }

    private void mergeLegacyKeys(Map<String, byte[]> map, String legacyKekMapJson) {
        if (!StringUtils.hasText(legacyKekMapJson)) {
            return;
        }
        try {
            Map<String, String> legacy = objectMapper.readValue(
                    legacyKekMapJson.trim(), new TypeReference<>() {});
            if (legacy == null || legacy.isEmpty()) {
                return;
            }
            for (Map.Entry<String, String> entry : legacy.entrySet()) {
                if (!StringUtils.hasText(entry.getKey()) || !StringUtils.hasText(entry.getValue())) {
                    continue;
                }
                String keyId = entry.getKey().trim();
                if (map.containsKey(keyId)) {
                    continue;
                }
                map.put(keyId, MessageKekMaterial.toAesKeyBytes(entry.getValue()));
            }
        } catch (Exception e) {
            throw new IllegalStateException("MESSAGE_KEK_LEGACY_MAP JSON 解析失败: " + e.getMessage(), e);
        }
    }
}
