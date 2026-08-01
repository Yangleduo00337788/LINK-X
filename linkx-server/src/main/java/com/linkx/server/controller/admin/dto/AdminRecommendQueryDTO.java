package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "推荐位 列表查询")
public class AdminRecommendQueryDTO extends AdminPageQueryDTO {

    @Schema(description = "状态：draft/published/unpublished")
    private String recommendStatus;

    @Schema(description = "推荐位：discover/chat_sidebar/moments")
    private String slotCode;
}
