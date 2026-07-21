package com.linkx.server.controller.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ChatSearchHitVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long messageId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    private String conversationName;
    private Integer conversationType;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long senderId;

    private String senderNickname;
    private String type;
    private String content;
    private String fileName;
    private String fileUrl;
    private Long createTime;
}
