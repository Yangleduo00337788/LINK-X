package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendMessageDTO {

    @NotNull(message = "会话 ID 不能为空")
    private Long conversationId;

    @NotBlank(message = "消息类型不能为空")
    private String msgType;

    private String content;

    private String fileName;

    private Long fileSize;

    private String fileUrl;

    /**
     * 语音时长（秒），语音消息专用
     */
    private Integer voiceDuration;

    private String clientMsgId;
}
