package com.linkx.server.task;

import com.linkx.server.config.LinkxProperties;
import com.linkx.server.entity.ImMessage;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.service.ComplianceService;
import com.linkx.server.service.FileStorageService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 消息留存策略：定期逻辑删除超过保留期的历史消息，并清理 MinIO 附件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageRetentionTask {

    private static final int BATCH_SIZE = 5000;
    /** 单次任务最多循环轮次，防止异常数据导致死循环 */
    private static final int MAX_ROUNDS = 50;

    private final ImMessageMapper messageMapper;
    private final LinkxProperties linkxProperties;
    private final ComplianceService complianceService;
    private final FileStorageService fileStorageService;

    /** 每天凌晨 3 点执行 */
    @Scheduled(cron = "0 0 3 * * ?")
    @SchedulerLock(name = "messageRetention_purgeExpiredMessages", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    public void purgeExpiredMessages() {
        int retentionDays = linkxProperties.getRetention().getMessageDays();
        if (retentionDays <= 0) {
            log.debug("消息留存未启用（retention.message-days<=0）");
            return;
        }

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -retentionDays);
        Date cutoff = cal.getTime();

        int total = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            List<ImMessage> expired = messageMapper.selectListByQuery(
                    QueryWrapper.create()
                            .where(ImMessage::getCreateTime).lt(cutoff)
                            .and(ImMessage::getDeleted).eq(0)
                            .limit(BATCH_SIZE)
            );
            if (expired.isEmpty()) {
                break;
            }

            // 先收集附件 key，再批量逻辑删除
            List<String> objectKeys = new ArrayList<>();
            List<Long> ids = new ArrayList<>(expired.size());
            for (ImMessage msg : expired) {
                ids.add(msg.getId());
                if (StringUtils.hasText(msg.getFileUrl())
                        && !"redPacket".equals(msg.getType())
                        && !"conference".equals(msg.getType())) {
                    objectKeys.add(msg.getFileUrl());
                }
            }

            // 批量 UPDATE：清空内容并标记删除，避免逐条往返
            ImMessage patch = new ImMessage();
            patch.setContent(null);
            patch.setFileUrl(null);
            patch.setFileName(null);
            patch.setDeleted(1);
            messageMapper.updateByQuery(patch,
                    QueryWrapper.create().where(ImMessage::getId).in(ids));

            // 提交后异步删 MinIO（失败仅打日志，依赖下次任务/合规清理兜底）
            for (String key : objectKeys) {
                fileStorageService.deleteFileAsync(key);
            }

            total += ids.size();
            if (expired.size() < BATCH_SIZE) {
                break;
            }
        }

        if (total > 0) {
            complianceService.audit(null, "retention",
                    "留存清理删除 " + total + " 条消息，cutoff=" + cutoff, true);
        }
        log.info("消息留存清理完成: deleted={}, cutoff={}, retentionDays={}", total, cutoff, retentionDays);
    }
}
