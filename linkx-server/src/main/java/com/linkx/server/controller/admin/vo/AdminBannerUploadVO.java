package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Banner 图片上传结果")
public class AdminBannerUploadVO {

    @Schema(description = "对象存储 key，保存 Banner 时提交到 imageUrl")
    private String objectKey;

    @Schema(description = "预览用临时 URL")
    private String url;
}
