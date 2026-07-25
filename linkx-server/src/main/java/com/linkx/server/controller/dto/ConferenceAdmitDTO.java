package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConferenceAdmitDTO {

    @NotNull(message = "会议ID不能为空")
    private Long conferenceId;

    @NotNull(message = "目标用户ID不能为空")
    private Long targetUserId;
}
