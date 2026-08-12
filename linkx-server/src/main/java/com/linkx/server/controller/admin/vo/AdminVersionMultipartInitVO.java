package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "安装包分片上传初始化结果")
public class AdminVersionMultipartInitVO {

    private String uploadId;
    private String objectKey;
}
