package com.linkx.server.controller.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateDriveItemDTO {
    @Size(max = 255)
    private String name;

    /** 目标文件夹 ID，空字符串表示移到根目录 */
    private String folderId;

    @Size(max = 1000)
    private String description;
}
