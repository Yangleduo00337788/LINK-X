package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class ConferenceSignalDTO {

    @NotNull(message = "会议ID不能为空")
    private Long conferenceId;

    @NotBlank(message = "信令类型不能为空")
    @Pattern(regexp = "offer|answer|ice-candidate", message = "信令类型必须为 offer、answer 或 ice-candidate")
    private String signalType;

    private String sdp;
    private String candidate;
    private Long targetUserId;
}
