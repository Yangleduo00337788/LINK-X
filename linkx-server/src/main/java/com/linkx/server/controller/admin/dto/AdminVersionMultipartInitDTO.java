package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "安装包分片上传初始化")
public class AdminVersionMultipartInitDTO {

    @NotBlank(message = "文件名不能为空")
    @Schema(description = "原始文件名，如 LinkX-Installer-1.0.0.exe")
    private String fileName;
}
