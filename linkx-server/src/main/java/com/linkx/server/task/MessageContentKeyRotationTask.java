package com.linkx.server.task;


/**
 * 作者：yangleduo
 */
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.repository.ImMessageRepository;
import com.linkx.server.security.crypto.MessageContentCipher;
import com.linkx.server.security.crypto.MessageKekProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 将使用旧 keyId 加密的消息批量轮换为当前 {@code MESSAGE_KEK_KEY_ID}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageContentKeyRotationTask {

    private final ImMessageRepository imMessageRepository;
    private final MessageContentCipher messageContentCipher;
    private final MessageKekProvider messageKekProvider;
    private final LinkxProperties linkxProperties;

    public RotationResult rotateBatch() {
        if (!messageContentCipher.isEnabled()) {
            log.debug("消息落库加密未启用，跳过密钥轮换任务");
            return RotationResult.skipped();
        }
        int batchSize = linkxProperties.getMessageEncryption().getKeyRotateBatchSize();
        long pendingBefore = imMessageRepository.countPendingKeyRotation();
        if (pendingBefore == 0) {
            log.debug("无待轮换密钥的消息，跳过");
            return new RotationResult(0, 0, 0);
        }
        int updated = imMessageRepository.rotateEncryptedKeyBatch(batchSize);
        long remaining = imMessageRepository.countPendingKeyRotation();
        log.info("消息密钥轮换批次完成: updated={}, remaining={}, batchSize={}, currentKeyId={}",
                updated, remaining, batchSize, messageKekProvider.currentKeyId());
        return new RotationResult(updated, remaining, pendingBefore);
    }

    public record RotationResult(int updated, long remaining, long pendingBefore) {
        public static RotationResult skipped() {
            return new RotationResult(0, -1, -1);
        }

        public boolean wasSkipped() {
            return pendingBefore < 0;
        }
    }
}
