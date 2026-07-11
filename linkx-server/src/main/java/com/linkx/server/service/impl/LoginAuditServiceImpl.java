package com.linkx.server.service.impl;

import com.linkx.server.entity.SysLoginAudit;
import com.linkx.server.mapper.SysLoginAuditMapper;
import com.linkx.server.service.LoginAuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class LoginAuditServiceImpl implements LoginAuditService {

    private final SysLoginAuditMapper loginAuditMapper;

    @Async
    @Override
    public void record(Long userId, String username, String ip, String userAgent, boolean success, String reason) {
        SysLoginAudit audit = SysLoginAudit.builder()
                .userId(userId)
                .username(username)
                .ip(ip)
                .userAgent(truncate(userAgent, 512))
                .success(success ? 1 : 0)
                .reason(truncate(reason, 255))
                .createTime(new Date())
                .build();
        loginAuditMapper.insert(audit);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
