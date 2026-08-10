package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "首页编排区块")
public class AdminHomepageSectionVO {

    private Long id;
    private String sectionType;
    private String sectionKey;
    private String title;
    private Boolean enabled;
    private Integer sortOrder;
    @Schema(description = "已发布内容数量")
    private Long publishedCount;
    @Schema(description = "管理端跳转路径提示")
    private String managePath;
}
