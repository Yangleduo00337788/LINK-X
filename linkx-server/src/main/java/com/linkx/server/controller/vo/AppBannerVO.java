package com.linkx.server.controller.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "客户端运营 Banner")
public class AppBannerVO {

    private Long id;
    private String title;
    private String imageUrl;
    private String linkUrl;
    private String position;
    private Integer sortOrder;
}
