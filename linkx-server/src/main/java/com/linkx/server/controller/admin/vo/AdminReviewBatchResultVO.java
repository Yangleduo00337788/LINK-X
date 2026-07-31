package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@Schema(description = "批量审核结果")
public class AdminReviewBatchResultVO {

    private int successCount;
    private int failCount;
    private List<FailureItem> failures;

    @Data
    @Builder
    @Schema(description = "失败项")
    public static class FailureItem {
        private Long id;
        private String reason;
    }
}
