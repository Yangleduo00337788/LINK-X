package com.linkx.server.task;

import com.linkx.server.service.RedPacketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 红包定时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedPacketTask {

    private final RedPacketService redPacketService;

    /**
     * 每分钟检查过期红包并退款
     */
    @Scheduled(cron = "0 * * * * ?")
    public void expireRedPackets() {
        try {
            log.info("开始执行红包过期检查任务");
            redPacketService.expireRedPackets();
            log.info("红包过期检查任务完成");
        } catch (Exception e) {
            log.error("红包过期检查任务执行失败", e);
        }
    }
}
