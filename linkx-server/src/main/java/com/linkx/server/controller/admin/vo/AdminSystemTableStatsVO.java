package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@Schema(description = "数据库表体量")
public class AdminSystemTableStatsVO {

    private LocalDateTime refreshedAt;
    private String schemaName;
    private AdminSystemStorageSummaryVO storage;
    private List<AdminSystemTableStatVO> tables;
    @Schema(description = "information_schema 行数为估算值")
    private Boolean rowCountApproximate;
    @Schema(description = "是否来自缓存")
    private Boolean cached;
}
