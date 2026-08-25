package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "管理端创建短视频话题")
public class AdminShortVideoTopicCreateDTO {

    @NotBlank
    @Size(max = 32)
    @Schema(description = "话题名（不含#）")
    private String name;

    @Size(max = 64)
    @Schema(description = "展示名")
    private String displayName;

    @Schema(description = "是否置顶")
    private Boolean pinned;

    @Schema(description = "置顶排序")
    private Integer pinOrder;
}
