package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "结束灵伴语音通话")
public class LinkMateVoiceCallHangupDTO {

    @NotBlank
    @Schema(description = "通话 ID")
    private String callId;

    @Schema(description = "客户端上报的通话时长（秒）")
    private Integer durationSec;
}
