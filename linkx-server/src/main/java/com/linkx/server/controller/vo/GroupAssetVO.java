package com.linkx.server.controller.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GroupAssetVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    /** file / image / essence */
    private String type;

    private String title;
    private String content;
    private String fileName;
    private Long fileSize;
    /** 可访问 URL（预签名） */
    private String fileUrl;
    private Integer downloadCount;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long messageId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long uploaderId;

    private String uploaderNickname;
    private String createTime;
}
