package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@Builder
@Schema(description = "审核任务")
public class AdminReviewVO {

    private Long id;
    private String sourceType;
    private String targetType;
    private String targetId;
    /** 涉事用户 ID（举报对象或敏感词命中作者） */
    private Long subjectUserId;
    private Long reporterUserId;
    private String reporterUsername;
    private String title;
    private String contentSnapshot;
    /** 证据图片预签名 URL（由 contentSnapshot 中的 object key 签发） */
    private List<String> evidenceUrls;
    private String riskLevel;
    private String status;
    private Long feedbackId;
    private String resolution;
    private Long resolvedBy;
    private Date resolvedAt;
    private Date createTime;
    @Schema(description = "是否超过审核 SLA")
    private Boolean overdue;
    @Schema(description = "是否已督办")
    private Boolean escalated;
    @Schema(description = "督办次数")
    private Integer escalationCount;
    @Schema(description = "最近督办时间")
    private Date escalatedAt;
}
