package com.linkx.server.controller.vo;


/**
 * 作者：yangleduo
 */
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConferenceInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String title;
    private String type;
    /** call=电话 meeting=会议 */
    private String scene;
    private Long creatorId;
    private Long conversationId;
    private Integer status;
    private Integer maxParticipants;
    private Date startTime;
    private Date endTime;
    private String callId;

    /** 是否设置了入会密码（不回传明文/哈希） */
    private Boolean hasPassword;

    /** 是否开启等候室 */
    private Boolean lobbyEnabled;

    /** 当前用户是否仍在等候室（未准入） */
    private Boolean waitingAdmit;

    /** 是否复用同会话已有 ACTIVE 会议 */
    private Boolean reused;

    @Builder.Default
    private List<Map<String, Object>> participants = new ArrayList<>();
}
