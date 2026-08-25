package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "管理端更新短视频话题")
public class AdminShortVideoTopicUpdateDTO {

    @Size(max = 64)
    @Schema(description = "展示名，传空字符串可清除")
    private String displayName;

    @Schema(description = "是否置顶")
    private Boolean pinned;

    @Schema(description = "置顶排序")
    private Integer pinOrder;

    @Schema(description = "状态 1展示 0隐藏")
    private Integer status;
}
