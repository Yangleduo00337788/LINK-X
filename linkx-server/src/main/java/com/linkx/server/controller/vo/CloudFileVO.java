package com.linkx.server.controller.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CloudFileVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** chat_message / group_asset */
    private String source;

    private String title;
    private String fileName;
    private Long fileSize;
    private String fileUrl;
    /** document / image / media / other */
    private String category;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    private String conversationName;
    private String senderName;
    private Long createTime;
}
