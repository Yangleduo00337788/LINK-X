package com.linkx.server.controller.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
@Schema(description = "客户端首页编排")
public class AppHomepageVO {

    private List<AppHomepageSectionVO> sections;

    @Data
    @Builder
    public static class AppHomepageSectionVO {
        private String sectionType;
        private String sectionKey;
        private String title;
        private Integer sortOrder;
        @Schema(description = "区块数据，结构随 sectionType 变化")
        private Object payload;
    }
}
