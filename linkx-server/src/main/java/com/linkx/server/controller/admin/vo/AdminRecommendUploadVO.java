package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "推荐位图片上传结果")
public class AdminRecommendUploadVO {

    private String objectKey;
    private String url;
}
