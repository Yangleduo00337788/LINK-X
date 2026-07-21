package com.linkx.server.controller.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GroupAnnouncementVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    private String content;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long publisherId;

    private String publisherNickname;

    /** 发布者角色：owner / admin / member */
    private String publisherRole;

    private Boolean pinned;

    private String createTime;

    private String updateTime;
}
