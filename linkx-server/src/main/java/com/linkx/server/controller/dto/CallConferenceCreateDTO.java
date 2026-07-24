package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CallConferenceCreateDTO {

    @NotNull(message = "会话ID不能为空")
    private Long conversationId;

    @Pattern(regexp = "voice|video", message = "通话类型必须为 voice 或 video")
    private String callType = "voice";
}
