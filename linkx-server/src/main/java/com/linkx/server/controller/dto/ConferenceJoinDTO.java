package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConferenceJoinDTO {

    @NotNull(message = "会议ID不能为空")
    private Long conferenceId;

    /** 可选；会议未设密码时可空 */
    @Size(max = 64, message = "会议密码最多64字符")
    private String password;
}
