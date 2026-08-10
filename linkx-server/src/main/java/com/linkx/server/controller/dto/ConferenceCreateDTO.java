package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConferenceCreateDTO {

    @NotNull(message = "会话ID不能为空")
    private Long conversationId;

    /** voice / video */
    private String type = "video";

    /**
     * 场景：{@code call}=语音/视频电话，{@code meeting}=会议。
     * 缺省 meeting，兼容旧客户端。
     */
    private String scene = "meeting";

    private String title;

    private String password;

    private Integer maxParticipants;

    /** 是否开启等候室 */
    private Boolean lobbyEnabled;
}
