package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateGroupAssetDTO {

    @NotBlank
    @Pattern(regexp = "^(file|image|essence)$", message = "type 必须为 file/image/essence")
    private String type;

    @Size(max = 255)
    private String title;

    @Size(max = 100000)
    private String content;

    @Size(max = 255)
    private String fileName;

    private Long fileSize;

    /** MinIO object key（上传接口返回） */
    @Size(max = 500)
    private String fileKey;

    private Long messageId;
}
