package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 移除成员 / 转让主持人等需要目标用户的会议操作 */
@Data
public class ConferenceMemberActionDTO {

    @NotNull(message = "会议ID不能为空")
    private Long conferenceId;

    @NotNull(message = "目标用户ID不能为空")
    private Long targetUserId;
}
