package com.linkx.server.service.impl;


/**
 * 作者：yangleduo
 */
import com.linkx.server.common.ClientIpResolver;
import com.linkx.server.entity.SysLoginAudit;
import com.linkx.server.mapper.SysLoginAuditMapper;
import com.linkx.server.service.LoginAuditService;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class LoginAuditServiceImpl implements LoginAuditService {

    private final SysLoginAuditMapper loginAuditMapper;

    @Async("auditExecutor")
    @Override
    public void record(Long userId, String username, String ip, String userAgent, boolean success, String reason) {
        SysLoginAudit audit = SysLoginAudit.builder()
                .userId(userId)
                .username(username)
                .ip(ClientIpResolver.normalizeToIpv4(ip))
                .userAgent(truncate(userAgent, 512))
                .success(success ? 1 : 0)
                .reason(truncate(reason, 255))
                .createTime(new Date())
                .build();
        loginAuditMapper.insert(audit);
    }

    @Override
    public List<String> recentSuccessfulIps(Long userId, int limit) {
        if (userId == null || limit <= 0) {
            return List.of();
        }
        int fetch = Math.min(Math.max(limit * 4, 20), 100);
        List<SysLoginAudit> rows = loginAuditMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(SysLoginAudit::getUserId).eq(userId)
                        .and(SysLoginAudit::getSuccess).eq(1)
                        .orderBy(SysLoginAudit::getCreateTime, false)
                        .limit(fetch));
        Set<String> ips = new LinkedHashSet<>();
        for (SysLoginAudit row : rows) {
            if (row == null || !StringUtils.hasText(row.getIp())) {
                continue;
            }
            ips.add(row.getIp().trim());
            if (ips.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(ips);
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
