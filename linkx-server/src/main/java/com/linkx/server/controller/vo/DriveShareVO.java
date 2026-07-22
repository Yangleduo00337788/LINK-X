package com.linkx.server.controller.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DriveShareVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String shareType;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetId;
    private String token;
    private String shareUrl;
    private boolean hasPassword;
    private Long expireAt;
    private Integer maxDownloads;
    private Integer downloadCount;
    private String targetName;
    private Long fileSize;
    private String fileUrl;
}
