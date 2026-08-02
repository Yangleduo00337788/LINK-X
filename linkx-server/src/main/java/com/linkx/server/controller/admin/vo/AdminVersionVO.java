package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "应用版本")
public class AdminVersionVO {

    private Long id;
    private String version;
    private String channel;
    private String releaseNotes;
    private String downloadUrl;
    private Boolean forceUpdate;
    private String minSupportedVersion;
    private String status;
    private Date publishedAt;
    private Long publishedBy;
    private Long createdBy;
    private Long updatedBy;
    private Date createTime;
    private Date updateTime;
}
