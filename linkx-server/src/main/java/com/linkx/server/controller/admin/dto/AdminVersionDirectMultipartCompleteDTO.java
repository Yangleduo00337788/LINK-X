package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "安装包直传分片完成")
public class AdminVersionDirectMultipartCompleteDTO {

    @NotBlank
    private String uploadId;

    @NotBlank
    private String objectKey;

    @NotBlank
    private String fileName;

    @NotNull
    @Positive
    private Long fileSize;

    @Schema(description = "安装包 SHA-256（64 位十六进制）")
    private String packageSha256;

    @NotEmpty
    @Valid
    private List<PartItem> parts;

    @Data
    public static class PartItem {
        @Positive
        private int partNumber;

        @NotBlank
        private String etag;
    }
}
