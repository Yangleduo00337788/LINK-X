package com.linkx.server.task;

import com.linkx.server.service.GroupService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 群禁言定时任务：开启/结束定时全体禁言，清理到期成员禁言
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GroupMuteTask {

    private final GroupService groupService;

    @Scheduled(cron = "0 * * * * ?")
    public void applyMuteSchedules() {
        try {
            groupService.applyMuteSchedules();
        } catch (Exception e) {
            log.error("群禁言定时任务执行失败", e);
        }
    }
}
