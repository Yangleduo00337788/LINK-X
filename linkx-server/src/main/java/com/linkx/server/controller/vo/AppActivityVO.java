package com.linkx.server.controller.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "客户端运营活动")
public class AppActivityVO {

    private Long id;
    private String title;
    private String coverUrl;
    private String linkUrl;
    private String description;
    private Integer sortOrder;
}
