package com.linkx.server.controller.admin.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "Banner 创建/更新")
public class AdminBannerDTO {

    @Schema(description = "标题")
    @NotBlank
    @Size(max = 128)
    private String title;

    @Schema(description = "图片对象 key（上传接口返回的 objectKey）")
    @NotBlank
    @Size(max = 1024)
    private String imageUrl;

    @Schema(description = "点击跳转 URL，可空")
    @Size(max = 1024)
    private String linkUrl;

    @Schema(description = "展位：home / login")
    @NotBlank
    @Size(max = 32)
    private String position;

    @Schema(description = "排序，越小越前")
    private Integer sortOrder;

    @Schema(description = "生效开始时间，毫秒时间戳")
    private Long startAt;

    @Schema(description = "生效结束时间，毫秒时间戳")
    private Long endAt;
}
