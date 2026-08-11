package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Schema(description = "对象存储配置更新")
public class StorageSettingUpdateDTO {

    @NotBlank
    @Schema(description = "存储提供商：minio | oss | local")
    private String provider;

    @Schema(description = "MinIO Endpoint")
    private String minioEndpoint;

    @Schema(description = "MinIO 桶名")
    private String minioBucketName;

    @Schema(description = "MinIO Access Key")
    private String minioAccessKey;

    @Schema(description = "MinIO Secret Key；留空表示不修改")
    private String minioSecretKey;

    @Schema(description = "OSS Endpoint")
    private String ossEndpoint;

    @Schema(description = "OSS 桶名")
    private String ossBucketName;

    @Schema(description = "OSS AccessKeyId")
    private String ossAccessKeyId;

    @Schema(description = "OSS AccessKeySecret；留空表示不修改")
    private String ossAccessKeySecret;

    @Schema(description = "OSS CNAME 域名")
    private String ossCnameDomain;

    @Schema(description = "本地存储根目录")
    private String localStoragePath;

    @NotNull
    @Schema(description = "单文件上传上限（字节）")
    private Long maxUploadBytes;
}
