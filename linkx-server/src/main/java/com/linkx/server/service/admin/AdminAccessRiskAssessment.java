package com.linkx.server.service.admin;

import lombok.Builder;
import lombok.Value;

/**
 * 管理端访问风险评估结果。
 */
@Value
@Builder
public class AdminAccessRiskAssessment {

    int score;
    boolean requireCaptcha;
    String level;

    public static AdminAccessRiskAssessment none() {
        return AdminAccessRiskAssessment.builder()
                .score(0)
                .requireCaptcha(false)
                .level("low")
                .build();
    }
}
