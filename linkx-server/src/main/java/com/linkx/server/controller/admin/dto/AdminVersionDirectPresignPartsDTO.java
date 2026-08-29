package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
@Schema(description = "安装包直传分片预签名请求")
public class AdminVersionDirectPresignPartsDTO {

    @NotBlank
    private String objectKey;

    @NotBlank
    private String uploadId;

    @Positive
    @Schema(description = "总分片数")
    private int totalParts;
}
