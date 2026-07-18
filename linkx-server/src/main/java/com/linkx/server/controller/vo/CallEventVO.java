package com.linkx.server.controller.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 通话相关 WebSocket / HTTP 事件载荷
 */
@Data
@Builder
public class CallEventVO {

    private String callId;
    private Long conversationId;
    private String callType;
    private String status;
    private Long fromUserId;
    private Long toUserId;
    private String fromNickname;
    private String fromAvatar;
    /** offer | answer | ice-candidate */
    private String signalType;
    private String sdp;
    private String candidate;
}
