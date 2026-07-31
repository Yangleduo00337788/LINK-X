package com.linkx.server.controller.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "黑名单条目")
public class AdminBlacklistVO {

    private Long id;
    private Long userId;
    private String username;
    private String nickname;
    private String reason;
    private String status;
    private Long createdBy;
    private String createdByName;
    private Long releasedBy;
    private String releasedByName;
    private Date releasedAt;
    private String releaseReason;
    private Date createTime;
}
