package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SaveFavoriteTagDTO {

    @NotBlank(message = "标签名不能为空")
    @Size(max = 64)
    private String name;

    @Size(max = 16)
    private String color;
}
