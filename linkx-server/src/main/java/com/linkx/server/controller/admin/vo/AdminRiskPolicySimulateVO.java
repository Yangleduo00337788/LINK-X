package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "风控策略模拟结果")
public class AdminRiskPolicySimulateVO {

    private boolean sensitiveFilterEnabled;
    private boolean blocked;
    private boolean filtered;
    private boolean alerted;
    private String filteredText;
    private List<String> matchedWords;
    private List<MatchedWordDetail> matchedDetails;
    private int riskScore;
    private String riskLevel;
    private List<String> riskFactors;

    @Data
    @Builder
    public static class MatchedWordDetail {
        private String word;
        private String action;
    }
}
