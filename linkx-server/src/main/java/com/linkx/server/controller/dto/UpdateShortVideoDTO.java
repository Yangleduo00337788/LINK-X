package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateShortVideoDTO {

    @Size(max = 2000, message = "描述最多2000字")
    private String description;

    @Pattern(regexp = "^[A-Za-z0-9_\\-/.]{1,256}$", message = "coverKey 格式非法")
    private String coverKey;

    @Min(value = 0, message = "可见性非法")
    @Max(value = 2, message = "可见性非法")
    private Integer visibility;
}
