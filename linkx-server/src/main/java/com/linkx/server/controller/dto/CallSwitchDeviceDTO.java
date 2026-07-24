package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class CallSwitchDeviceDTO {

    @NotBlank(message = "通话ID不能为空")
    private String callId;

    @NotBlank(message = "设备类型不能为空")
    @Pattern(regexp = "audio|video", message = "设备类型必须为 audio 或 video")
    private String deviceType;

    @NotNull(message = "enabled 不能为空")
    private Boolean enabled;
}
