package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "安装包上传能力")
public class AdminVersionUploadCapabilityVO {

    @Schema(description = "是否支持浏览器直传对象存储（MinIO/R2）")
    private boolean directMultipart;

    @Schema(description = "当前活跃存储提供商")
    private String provider;

    @Schema(description = "分片大小（字节）")
    private int chunkSize;

    @Schema(description = "建议并发数")
    private int maxConcurrency;
}
