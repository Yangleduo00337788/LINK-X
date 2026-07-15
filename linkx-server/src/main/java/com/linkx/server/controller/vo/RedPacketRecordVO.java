package com.linkx.server.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedPacketRecordVO {

    private Long id;

    private Long userId;

    private String nickname;

    private String avatar;

    private BigDecimal amount;

    private Boolean isLucky;

    private String time;
}
