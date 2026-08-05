package com.linkx.server.service.admin.impl;

import com.linkx.server.config.LinkxProperties;
import com.linkx.server.entity.admin.SysRiskEvent;
import com.linkx.server.mapper.admin.SysRiskEventMapper;
import com.linkx.server.service.admin.AdminAccessRiskAssessment;
import com.linkx.server.service.admin.AdminAccessRiskService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Date;

@Service
@RequiredArgsConstructor
public class AdminAccessRiskServiceImpl implements AdminAccessRiskService {

    private static final String IP_FAIL_KEY = "linkx:admin:risk:login-fail:ip:";
    private static final String USER_DEVICE_KEY = "linkx:admin:risk:device:user:";
    private static final Duration FAIL_TTL = Duration.ofHours(2);
    private static final Duration DEVICE_TTL = Duration.ofDays(90);

    private final StringRedisTemplate redisTemplate;
    private final LinkxProperties linkxProperties;
    private final SysRiskEventMapper sysRiskEventMapper;

    @Override
    public AdminAccessRiskAssessment evaluatePreLogin(String ip, String deviceId) {
        int score = 0;
        if (StringUtils.hasText(ip)) {
            String fails = redisTemplate.opsForValue().get(IP_FAIL_KEY + ip.trim());
            if (StringUtils.hasText(fails)) {
                try {
                    score += Math.min(50, Integer.parseInt(fails.trim()) * 12);
                } catch (NumberFormatException ignored) {
                    score += 20;
                }
            }
        }
        if (!StringUtils.hasText(deviceId)) {
            score += 10;
        }
        return build(score);
    }

    @Override
    public AdminAccessRiskAssessment evaluatePostLogin(Long userId, String ip, String deviceId, boolean newLoginIp) {
        int score = 0;
        if (newLoginIp) {
            score += 25;
        }
        if (userId != null && StringUtils.hasText(deviceId)) {
            String key = USER_DEVICE_KEY + userId + ":" + deviceId.trim();
            Boolean seen = redisTemplate.hasKey(key);
            if (!Boolean.TRUE.equals(seen)) {
                score += 30;
                redisTemplate.opsForValue().set(key, "1", DEVICE_TTL);
            }
        } else if (!StringUtils.hasText(deviceId)) {
            score += 15;
        }
        AdminAccessRiskAssessment assessment = build(score);
        if (assessment.getScore() >= linkxProperties.getRiskPolicy().getScoreMediumMin()) {
            recordRiskEvent(userId, ip, deviceId, assessment);
        }
        return assessment;
    }

    @Override
    public void recordLoginFailure(String ip) {
        if (!StringUtils.hasText(ip)) {
            return;
        }
        String key = IP_FAIL_KEY + ip.trim();
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, FAIL_TTL);
        }
    }

    @Override
    public void clearLoginFailures(String ip) {
        if (StringUtils.hasText(ip)) {
            redisTemplate.delete(IP_FAIL_KEY + ip.trim());
        }
    }

    private AdminAccessRiskAssessment build(int score) {
        LinkxProperties.RiskPolicy policy = linkxProperties.getRiskPolicy();
        String level = "low";
        if (score >= policy.getScoreCriticalMin()) {
            level = "critical";
        } else if (score >= policy.getScoreHighMin()) {
            level = "high";
        } else if (score >= policy.getScoreMediumMin()) {
            level = "medium";
        }
        boolean requireCaptcha = score >= policy.getScoreMediumMin();
        return AdminAccessRiskAssessment.builder()
                .score(score)
                .requireCaptcha(requireCaptcha)
                .level(level)
                .build();
    }

    private void recordRiskEvent(Long userId, String ip, String deviceId, AdminAccessRiskAssessment assessment) {
        String detail = "score=" + assessment.getScore()
                + ", level=" + assessment.getLevel()
                + (StringUtils.hasText(deviceId) ? ", deviceId=" + deviceId.trim() : "");
        SysRiskEvent event = SysRiskEvent.builder()
                .eventType("ADMIN_ACCESS_RISK")
                .title("管理端访问风险")
                .detail(detail)
                .riskLevel(mapLevel(assessment.getLevel()))
                .status(SysRiskEvent.STATUS_PENDING)
                .userId(userId)
                .ip(ip)
                .createTime(new Date())
                .updateTime(new Date())
                .build();
        sysRiskEventMapper.insert(event);
    }

    private static String mapLevel(String level) {
        if ("critical".equals(level) || "high".equals(level)) {
            return SysRiskEvent.LEVEL_HIGH;
        }
        if ("medium".equals(level)) {
            return SysRiskEvent.LEVEL_MEDIUM;
        }
        return SysRiskEvent.LEVEL_LOW;
    }
}
