package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "安装包上传结果")
public class AdminVersionUploadVO {

    @Schema(description = "对象存储 key，保存版本时提交到 downloadUrl")
    private String objectKey;

    @Schema(description = "预览/复制用下载链接")
    private String url;

    @Schema(description = "安装包 SHA-256")
    private String sha256;

    @Schema(description = "原始文件名")
    private String fileName;

    @Schema(description = "文件大小（字节）")
    private Long fileSize;
}
