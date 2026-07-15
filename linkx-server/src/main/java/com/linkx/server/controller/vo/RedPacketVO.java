package com.linkx.server.controller.vo;

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

    private List<RedPacketRecordVO> records;
}
