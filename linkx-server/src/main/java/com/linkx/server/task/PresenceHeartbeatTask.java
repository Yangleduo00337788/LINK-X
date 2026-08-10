package com.linkx.server.task;


/**
 * 作者：yangleduo
 */
import com.linkx.server.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Presence 实例心跳与宕机清扫：心跳 5s 一次，清扫间隔 5s，假在线收敛到约 15s 内。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceHeartbeatTask {

    private final PresenceService presenceService;

    public void heartbeatAndSweep() {
        try {
            presenceService.refreshInstanceHeartbeat();
            presenceService.sweepDeadInstances();
        } catch (Exception e) {
            log.warn("presence 心跳/清扫异常: {}", e.getMessage());
        }
    }
}
