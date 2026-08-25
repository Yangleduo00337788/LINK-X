package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "管理端短视频作品查询")
public class AdminShortVideoPostQueryDTO extends AdminPageQueryDTO {

    @Schema(description = "作者用户 ID")
    private Long userId;

    @Schema(description = "可见性 0公开 1好友 2私密")
    private Integer visibility;

    @Schema(description = "转码状态")
    private String transcodeStatus;
}
