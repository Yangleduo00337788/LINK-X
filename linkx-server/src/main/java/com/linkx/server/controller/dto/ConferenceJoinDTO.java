package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConferenceJoinDTO {

    @NotNull(message = "会议ID不能为空")
    private Long conferenceId;

    /** 可选；会议未设密码时可空 */
    private String password;
}
