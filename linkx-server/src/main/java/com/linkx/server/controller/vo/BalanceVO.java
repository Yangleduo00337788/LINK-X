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
public class BalanceVO {

    private Long userId;

    private BigDecimal balance;

    private BigDecimal frozen;

    private BigDecimal available;

    private BigDecimal totalRecharge;

    private BigDecimal totalWithdraw;
}
