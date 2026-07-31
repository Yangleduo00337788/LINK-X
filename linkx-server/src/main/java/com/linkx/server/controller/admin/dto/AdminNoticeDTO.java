package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "公告创建/更新")
public class AdminNoticeDTO {

    @Schema(description = "标题")
    @NotBlank
    @Size(max = 128)
    private String title;

    @Schema(description = "正文")
    @NotBlank
    @Size(max = 20000)
    private String content;

    @Schema(description = "目标端：admin=管理端 / client=客户端")
    @NotBlank
    @Size(max = 16)
    private String targetSide;
}
