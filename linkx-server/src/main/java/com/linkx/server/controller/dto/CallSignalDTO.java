package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * WebRTC 信令中继（SDP / ICE）
 */
@Data
public class CallSignalDTO {

    @NotBlank(message = "通话ID不能为空")
    private String callId;

    @NotBlank(message = "信令类型不能为空")
    @Pattern(regexp = "offer|answer|ice-candidate", message = "信令类型必须为 offer、answer 或 ice-candidate")
    private String signalType;

    /** SDP（offer/answer） */
    private String sdp;

    /** ICE candidate JSON 字符串 */
    private String candidate;
}
