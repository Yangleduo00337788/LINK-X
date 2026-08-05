package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "风控自定义规则")
public class AdminRiskRuleVO {

    private Long id;
    private String name;
    private String scope;
    private String keyword;
    private String conditionJson;
    private Integer scoreDelta;
    private String actionType;
    private String actionConfig;
    private Integer priority;
    private Boolean enabled;
    private Date createTime;
    private Date updateTime;
}
