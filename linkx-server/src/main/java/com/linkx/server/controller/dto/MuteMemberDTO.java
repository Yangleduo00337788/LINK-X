package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 指定成员禁言
 */
@Data
public class MuteMemberDTO {

    /** 是否禁言 */
    @NotNull(message = "muted 不能为空")
    private Boolean muted;

    /**
     * 禁言截止时间（毫秒）；为空表示直到手动解除
     */
    private Long muteUntil;
}
