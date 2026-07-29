package com.linkx.server.controller.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 余额充值（演示/测试通道，无真实支付）。
 */
@Data
public class RechargeDTO {

    @NotNull(message = "充值金额不能为空")
    @DecimalMin(value = "0.01", message = "充值金额最小为0.01")
    @DecimalMax(value = "1000", message = "单次充值最大为1000元")
    private BigDecimal amount;
}
