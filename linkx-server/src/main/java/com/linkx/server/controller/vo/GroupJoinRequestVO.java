package com.linkx.server.controller.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GroupJoinRequestVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long applicantId;

    private String applicantNickname;

    private String applicantAvatar;

    private String message;

    private Long createTime;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long notificationId;
}
