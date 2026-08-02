package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "应用版本列表查询")
public class AdminVersionQueryDTO extends AdminPageQueryDTO {

    @Schema(description = "版本状态：draft/published/archived")
    private String versionStatus;

    @Schema(description = "发布渠道：stable/beta/dev")
    private String channel;
}
