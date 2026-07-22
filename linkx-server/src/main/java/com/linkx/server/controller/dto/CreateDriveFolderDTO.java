package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateDriveFolderDTO {
    /** 父文件夹 ID，空=根目录 */
    private String parentId;

    @NotBlank(message = "文件夹名不能为空")
    @Size(max = 255, message = "文件夹名过长")
    private String name;
}
