package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "敏感词")
public class AdminSensitiveWordVO {

    private Long id;
    private String word;
    private String category;
    private String action;
    private String replacement;
    private Boolean enabled;
    private Date createTime;
    private Date updateTime;
}
