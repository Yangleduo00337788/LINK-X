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

    /** 客户端本地当天日期 YYYY-MM-DD */
    @Size(max = 16)
    private String todayDate;

    /** 近期会话列表摘要（好友/群聊名称与 ID） */
    @Size(max = 4000)
    private String recentSessions;
}
