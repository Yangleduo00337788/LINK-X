package com.linkx.server.task;


/**
 * 作者：yangleduo
 */
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.repository.ImMessageRepository;
import com.linkx.server.security.crypto.MessageContentCipher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 历史 IM 消息明文批量重加密（需已开启 {@code MESSAGE_CONTENT_ENCRYPT_ENABLED}）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageContentReencryptTask {

    private final ImMessageRepository imMessageRepository;
    private final MessageContentCipher messageContentCipher;
    private final LinkxProperties linkxProperties;

    public ReencryptResult reencryptBatch() {
        if (!messageContentCipher.isEnabled()) {
            log.debug("消息落库加密未启用，跳过重加密任务");
            return ReencryptResult.skipped();
        }
        int batchSize = linkxProperties.getMessageEncryption().getReencryptBatchSize();
        long pendingBefore = imMessageRepository.countPendingReencrypt();
        if (pendingBefore == 0) {
            log.debug("无待重加密消息，跳过");
            return new ReencryptResult(0, 0, 0);
        }
        int updated = imMessageRepository.reencryptPlaintextBatch(batchSize);
        long remaining = imMessageRepository.countPendingReencrypt();
        log.info("消息历史重加密批次完成: updated={}, remaining={}, batchSize={}", updated, remaining, batchSize);
        return new ReencryptResult(updated, remaining, pendingBefore);
    }

    public record ReencryptResult(int updated, long remaining, long pendingBefore) {
        public static ReencryptResult skipped() {
            return new ReencryptResult(0, -1, -1);
        }

        public boolean wasSkipped() {
            return pendingBefore < 0;
        }
    }
}
