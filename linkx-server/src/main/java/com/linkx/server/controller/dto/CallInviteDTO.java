package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 发起通话邀请请求
 */
@Data
public class CallInviteDTO {

    @NotNull(message = "会话ID不能为空")
    private Long conversationId;

    @NotBlank(message = "通话类型不能为空")
    @Pattern(regexp = "voice|video", message = "通话类型必须为 voice 或 video")
    private String callType;
}
