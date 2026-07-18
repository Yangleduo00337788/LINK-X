package com.linkx.server.controller.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 通话邀请响应
 */
@Data
@Builder
public class CallInviteVO {

    private String callId;
    private Long conversationId;
    private String callType;
    private String status;
    private Long peerUserId;
    private String peerNickname;
    private String peerAvatar;
}
