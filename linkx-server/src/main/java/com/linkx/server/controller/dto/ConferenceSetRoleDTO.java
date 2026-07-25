package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConferenceSetRoleDTO {

    @NotNull(message = "会议ID不能为空")
    private Long conferenceId;

    @NotNull(message = "目标用户ID不能为空")
    private Long targetUserId;

    /** host / co-host / member；不可直接设 host（请用转让主持人） */
    @NotBlank(message = "角色不能为空")
    private String role;
}
