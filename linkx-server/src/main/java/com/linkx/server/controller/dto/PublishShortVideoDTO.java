package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PublishShortVideoDTO {

    @Size(max = 2000, message = "描述最多2000字")
    private String description;

    @NotBlank(message = "视频不能为空")
    @Pattern(regexp = "^[A-Za-z0-9_\\-/.]{1,256}$", message = "videoKey 格式非法")
    private String videoKey;

    @Pattern(regexp = "^[A-Za-z0-9_\\-/.]{1,256}$", message = "coverKey 格式非法")
    private String coverKey;

    @Min(value = 0, message = "时长非法")
    @Max(value = 3600000, message = "时长不能超过1小时")
    private Integer durationMs;

    @Min(value = 0, message = "可见性非法")
    @Max(value = 2, message = "可见性非法")
    private Integer visibility;
}
