package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 仅携带 conferenceId 的会议请求（离开 / 结束等）
 */
@Data
public class ConferenceIdDTO {

    @NotNull(message = "会议ID不能为空")
    private Long conferenceId;
}
