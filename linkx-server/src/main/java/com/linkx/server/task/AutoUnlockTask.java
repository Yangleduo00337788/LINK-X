package com.linkx.server.task;

import com.linkx.server.service.SysUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 登录失败自动封禁到期后，将账号状态由禁用恢复为启用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoUnlockTask {

    private final SysUserService sysUserService;

    public void unlockExpired() {
        try {
            int n = sysUserService.unlockExpiredAutoLocks();
            if (n > 0) {
                log.info("自动解封到期账号 {} 个", n);
            }
        } catch (Exception e) {
            log.error("自动解封任务执行失败", e);
        }
    }
}
