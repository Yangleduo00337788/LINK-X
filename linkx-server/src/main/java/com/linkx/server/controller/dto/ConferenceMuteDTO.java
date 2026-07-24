package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConferenceMuteDTO {

    @NotNull(message = "会议ID不能为空")
    private Long conferenceId;

    /** 为空时表示操作自己 */
    private Long targetUserId;

    @NotNull(message = "muted 不能为空")
    private Boolean muted;
}
