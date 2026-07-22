package com.linkx.server.controller.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DriveActivityVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String targetType;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long targetId;
    private String targetName;
    private String action;
    private String detail;
    private Long createTime;
}
