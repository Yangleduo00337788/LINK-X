package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConferenceVideoDTO {

    @NotNull(message = "会议ID不能为空")
    private Long conferenceId;

    @NotNull(message = "videoOff 不能为空")
    private Boolean videoOff;
}
