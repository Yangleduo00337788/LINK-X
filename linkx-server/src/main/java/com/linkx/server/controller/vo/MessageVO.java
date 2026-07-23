package com.linkx.server.controller.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MessageVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long conversationId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long senderId;

    private String senderNickname;
    private String senderAvatar;

    private String type;
    private String content;
    private String fileName;
    private Long fileSize;
    private String fileUrl;

    /**
     * 语音时长（秒），语音消息专用
     */
    private Integer voiceDuration;

    private Long createTime;
    private Boolean isSelf;

    private String clientMsgId;
    private String deliveryStatus;
    private Integer readStatus;
    private Long unreadCount;
    private String ackType;

    // ---------- 红包专属（仅 type=redPacket 时有值）----------

    /** 红包 ID */
    private String redPacketId;
    /** 红包祝福语 */
    private String redPacketGreeting;
    /** 红包总金额（元） */
    private java.math.BigDecimal redPacketTotalAmount;
    /** 红包类型：normal 普通 / lucky 拼手气 */
    private String redPacketType;
    /** 红包总个数 */
    private Integer redPacketTotalCount;
    /** 红包剩余个数 */
    private Integer redPacketRemainingCount;
    /** 当前用户是否已领取 */
    private Boolean redPacketReceived;
    /** 当前用户已领取金额（元） */
    private java.math.BigDecimal redPacketReceivedAmount;
    /** 红包状态：active / finished / expired */
    private String redPacketStatus;
}
