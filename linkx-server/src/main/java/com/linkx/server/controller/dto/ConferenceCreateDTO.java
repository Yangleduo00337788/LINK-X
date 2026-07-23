package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConferenceCreateDTO {

    @NotNull(message = "会话ID不能为空")
    private Long conversationId;

    /** voice / video */
    private String type = "video";

    private String title;

    private String password;

    private Integer maxParticipants;
}
