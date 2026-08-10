package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConferenceRaiseDTO {

    @NotNull(message = "会议ID不能为空")
    private Long conferenceId;

    @NotNull(message = "raised 不能为空")
    private Boolean raised;
}
