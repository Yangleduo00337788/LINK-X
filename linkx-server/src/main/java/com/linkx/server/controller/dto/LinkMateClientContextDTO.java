package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Agent 模式下客户端上报的当前 UI 状态，供模型决策。
 */
@Data
public class LinkMateClientContextDTO {

    @Size(max = 32)
    private String currentNav;

    @Size(max = 64)
    private String currentSessionId;

    @Size(max = 200)
    private String currentSessionTitle;
}
