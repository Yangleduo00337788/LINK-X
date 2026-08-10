package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "风控策略总览")
public class AdminRiskPolicyVO {

    private MessageStormPolicy messageStorm;
    private ScoreThresholdPolicy scoreThresholds;
    private RateLimitPolicy rateLimits;
    private LoginLockPolicy loginLock;
    private Boolean sensitiveFilterEnabled;

    @Data
    @Builder
    public static class MessageStormPolicy {
        private int userThreshold;
        private int userWindowSeconds;
        private int groupMinMembers;
        private int groupLargeMembers;
        private int groupMidPerMinute;
        private int groupLargePerMinute;
    }

    @Data
    @Builder
    public static class ScoreThresholdPolicy {
        private int mediumMin;
        private int highMin;
        private int criticalMin;
    }

    @Data
    @Builder
    public static class RateLimitPolicy {
        private int loginPerMinute;
        private int registerPerMinute;
        private int searchPerMinute;
        private int listPerMinute;
        private int writePerMinute;
        private int uploadPerMinute;
    }

    @Data
    @Builder
    public static class LoginLockPolicy {
        private int clientMaxAttempts;
        private int clientLockMinutes;
        private int adminMaxAttempts;
        private int adminLockMinutes;
    }
}
