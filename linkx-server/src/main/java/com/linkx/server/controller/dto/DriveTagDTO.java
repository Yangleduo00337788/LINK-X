package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DriveTagDTO {
    @NotBlank(message = "标签不能为空")
    @Size(max = 64, message = "标签过长")
    private String tagName;
}
