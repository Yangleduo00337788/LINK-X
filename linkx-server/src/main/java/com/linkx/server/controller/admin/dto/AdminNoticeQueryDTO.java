package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "公告列表查询")
public class AdminNoticeQueryDTO extends AdminPageQueryDTO {

    @Schema(description = "公告状态：draft/published/unpublished")
    private String noticeStatus;

    @Schema(description = "目标端：admin/client")
    private String targetSide;
}
