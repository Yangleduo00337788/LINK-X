package com.linkx.server.controller.vo;


/**
 * 作者：yangleduo
 */
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedPacketVO {

    private String id;

    private Long senderId;

    private String senderNickname;

    private String senderAvatar;

    private Long conversationId;

    private String type;

    private BigDecimal totalAmount;

    private Integer totalCount;

    private BigDecimal remainingAmount;

    private Integer remainingCount;

    private String greeting;

    private String status;

    private String time;

    private Boolean received;

    private BigDecimal receivedAmount;

    /** 当前用户是否为红包发送者（服务端计算，避免前端 Long 精度丢失误判） */
    private Boolean isSelf;

    private List<RedPacketRecordVO> records;
}
