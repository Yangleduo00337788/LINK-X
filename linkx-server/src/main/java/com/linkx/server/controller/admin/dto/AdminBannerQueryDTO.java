package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "Banner 列表查询")
public class AdminBannerQueryDTO extends AdminPageQueryDTO {

    @Schema(description = "Banner 状态：draft/published/unpublished")
    private String bannerStatus;

    @Schema(description = "展位：home/login")
    private String position;
}
