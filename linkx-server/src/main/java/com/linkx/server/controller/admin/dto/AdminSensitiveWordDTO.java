package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "敏感词创建/更新")
public class AdminSensitiveWordDTO {

    @Schema(description = "敏感词")
    @NotBlank
    @Size(max = 100)
    private String word;

    @Schema(description = "分类: general/politics/violence/ad")
    @Size(max = 32)
    private String category;

    @Schema(description = "处理方式: filter/block/alert")
    @NotBlank
    @Size(max = 20)
    private String action;

    @Schema(description = "替换文本")
    @Size(max = 10)
    private String replacement;

    @Schema(description = "是否启用")
    private Boolean enabled;
}
