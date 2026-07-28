package com.linkx.server.controller.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendMessageDTO {

    @NotNull(message = "会话 ID 不能为空")
    @Min(value = 1, message = "会话 ID 必须为正数")
    private Long conversationId;

    @NotBlank(message = "消息类型不能为空")
    @Pattern(regexp = "^(text|image|file|voice|video|system|recall|redpacket|location|datacard)$",
            message = "消息类型不合法")
    private String msgType;

    @Size(max = 8192, message = "文本内容最多8192字符")
    private String content;

    @Size(max = 255, message = "文件名最多255字符")
    private String fileName;

    @Min(value = 0, message = "文件大小不能为负数")
    private Long fileSize;

    @Size(max = 1024, message = "fileUrl 最多1024字符")
    private String fileUrl;

    /**
     * 语音时长（秒），语音消息专用
     */
    @Min(value = 0, message = "语音时长不能为负数")
    private Integer voiceDuration;

    /**
     * 客户端幂等 ID（可选）：传入时启用去重，未传入则跳过去重检查。
     * 与发送者 ID 组成唯一约束，防网络重试/双击重复发消息。
     */
    @Size(max = 128, message = "clientMsgId 最长128字符")
    private String clientMsgId;

    /**
     * 客户端期望的消息状态，主要用于未来扩展。
     */
    @Size(max = 32, message = "deliveryStatus 最长32字符")
    private String deliveryStatus;
}
