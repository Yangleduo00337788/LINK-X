package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConferenceTransferHostDTO {

    @NotNull(message = "会议ID不能为空")
    private Long conferenceId;

    @NotNull(message = "新主持人ID不能为空")
    private Long newHostId;
}
