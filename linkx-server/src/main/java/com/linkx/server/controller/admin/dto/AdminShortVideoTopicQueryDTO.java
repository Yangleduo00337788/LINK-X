package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "管理端短视频话题查询")
public class AdminShortVideoTopicQueryDTO extends AdminPageQueryDTO {

    @Schema(description = "是否置顶")
    private Boolean pinned;

    @Schema(description = "状态 1展示 0隐藏")
    private Integer status;
}
