package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "安装包直传初始化")
public class AdminVersionDirectMultipartInitVO {

    private String uploadId;
    private String objectKey;

    @Schema(description = "分片大小（字节），客户端须与此一致")
    private int chunkSize;

    @Schema(description = "建议并发上传数")
    private int maxConcurrency;
}
