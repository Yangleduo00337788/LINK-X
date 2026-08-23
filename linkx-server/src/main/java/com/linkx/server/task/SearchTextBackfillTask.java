package com.linkx.server.task;


/**
 * 作者：yangleduo
 */
import com.linkx.server.config.LinkxProperties;
import com.linkx.server.repository.ImMessageRepository;
import com.linkx.server.repository.MomentsPostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 历史 IM 消息 / 朋友圈动态 search_text 批量回填（供 FULLTEXT 检索）。
 * <p>
 * 对加密正文先解密再生成摘要，仅更新 {@code search_text} 列，不改密文正文。
 * </p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchTextBackfillTask {

    private final ImMessageRepository imMessageRepository;
    private final MomentsPostRepository momentsPostRepository;
    private final LinkxProperties linkxProperties;

    public BackfillResult backfillBatch() {
        int batchSize = linkxProperties.getMessageEncryption().getReencryptBatchSize();
        long pendingBefore = imMessageRepository.countPendingSearchTextBackfill()
                + momentsPostRepository.countPendingSearchTextBackfill();
        if (pendingBefore == 0) {
            log.debug("无待回填 search_text 的内容，跳过");
            return new BackfillResult(0, 0, 0);
        }
        int updated = imMessageRepository.backfillSearchTextBatch(batchSize)
                + momentsPostRepository.backfillSearchTextBatch(batchSize);
        long remaining = imMessageRepository.countPendingSearchTextBackfill()
                + momentsPostRepository.countPendingSearchTextBackfill();
        log.info("search_text 历史回填批次完成: updated={}, remaining={}, batchSize={}",
                updated, remaining, batchSize);
        return new BackfillResult(updated, remaining, pendingBefore);
    }

    public record BackfillResult(int updated, long remaining, long pendingBefore) {
    }
}
