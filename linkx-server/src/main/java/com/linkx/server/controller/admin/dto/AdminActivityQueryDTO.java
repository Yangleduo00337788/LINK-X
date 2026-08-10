package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "活动 列表查询")
public class AdminActivityQueryDTO extends AdminPageQueryDTO {

    @Schema(description = "状态：draft/published/unpublished")
    private String activityStatus;
}
