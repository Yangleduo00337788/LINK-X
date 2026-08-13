package com.linkx.server.config;


/**
 * 作者：yangleduo
 */
import com.linkx.server.security.crypto.MessageKekProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 消息落库加密启动校验：开启时必须配置有效 MESSAGE_KEK。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageEncryptionValidator {

    private final LinkxProperties linkxProperties;
    private final MessageKekProvider messageKekProvider;

    @PostConstruct
    public void validate() {
        LinkxProperties.MessageEncryption config = linkxProperties.getMessageEncryption();
        if (!config.isEnabled()) {
            log.info("[消息加密] 未启用（MESSAGE_CONTENT_ENCRYPT_ENABLED=false）");
            return;
        }
        if (!StringUtils.hasText(config.getKek())) {
            throw new IllegalStateException(
                    "MESSAGE_CONTENT_ENCRYPT_ENABLED=true 时必须设置 MESSAGE_KEK（建议: openssl rand -base64 32）");
        }
        messageKekProvider.resolveAesKey();
        if (StringUtils.hasText(config.getLegacyKekMap())) {
            log.info("[消息加密] 已配置历史 KEK，可用于解密 keyIds={}",
                    messageKekProvider.loadedKeyIds());
        } else {
            log.info("[消息加密] 已启用，当前 keyId={}", messageKekProvider.currentKeyId());
        }
    }
}
