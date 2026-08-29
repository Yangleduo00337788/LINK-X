package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "对象存储连通性测试")
public class TestStorageConnectionDTO {

    @NotBlank
    @Schema(description = "存储提供商：minio | oss | cos | r2")
    private String provider;

    private String minioEndpoint;
    private String minioBucketName;
    private String minioAccessKey;
    private String minioSecretKey;

    private String ossEndpoint;
    private String ossBucketName;
    private String ossAccessKeyId;
    private String ossAccessKeySecret;
    private String ossCnameDomain;

    private String cosRegion;
    private String cosBucketName;
    private String cosSecretId;
    private String cosSecretKey;
    private String cosCnameDomain;

    private String r2Endpoint;
    private String r2BucketName;
    private String r2AccessKeyId;
    private String r2SecretAccessKey;
    private String r2CnameDomain;
}
