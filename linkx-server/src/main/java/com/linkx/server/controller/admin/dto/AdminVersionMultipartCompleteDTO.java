package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
@Schema(description = "安装包分片上传完成")
public class AdminVersionMultipartCompleteDTO {

    @NotBlank(message = "uploadId 不能为空")
    private String uploadId;

    @NotBlank(message = "objectKey 不能为空")
    private String objectKey;

    @NotBlank(message = "文件名不能为空")
    private String fileName;

    @NotNull(message = "文件大小不能为空")
    @Positive(message = "文件大小无效")
    private Long fileSize;

    @Schema(description = "安装包 SHA-256（64 位十六进制，可选；提供后服务端不再从 OSS 回拉整包计算）")
    private String packageSha256;
}
