package com.linkx.server.task;

import com.linkx.server.config.LinkxProperties;
import com.linkx.server.entity.ImMessage;
import com.linkx.server.mapper.ImMessageMapper;
import com.linkx.server.service.ComplianceService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 消息留存策略：定期逻辑删除超过保留期的历史消息。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageRetentionTask {

    private final ImMessageMapper messageMapper;
    private final LinkxProperties linkxProperties;
    private final ComplianceService complianceService;

    /** 每天凌晨 3 点执行 */
    @Scheduled(cron = "0 0 3 * * ?")
    public void purgeExpiredMessages() {
        int retentionDays = linkxProperties.getRetention().getMessageDays();
        if (retentionDays <= 0) {
            log.debug("消息留存未启用（retention.message-days<=0）");
            return;
        }

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -retentionDays);
        Date cutoff = cal.getTime();

        List<ImMessage> expired = messageMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(ImMessage::getCreateTime).lt(cutoff)
                        .and(ImMessage::getDeleted).eq(0)
                        .limit(5000)
        );

        if (expired.isEmpty()) {
            return;
        }

        int count = 0;
        for (ImMessage msg : expired) {
            msg.setContent(null);
            msg.setFileUrl(null);
            msg.setFileName(null);
            msg.setDeleted(1);
            messageMapper.update(msg);
            count++;
        }

        complianceService.audit(null, "retention",
                "留存清理删除 " + count + " 条消息，cutoff=" + cutoff, true);
        log.info("消息留存清理完成: deleted={}, cutoff={}, retentionDays={}", count, cutoff, retentionDays);
    }
}
