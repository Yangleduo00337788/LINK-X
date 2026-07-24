package com.linkx.server.controller.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 发送红包请求
 */
@Data
public class SendRedPacketDTO {

    @NotNull(message = "会话ID不能为空")
    private Long conversationId;

    @NotBlank(message = "红包类型不能为空")
    @Pattern(regexp = "^(normal|lucky)$", message = "红包类型仅支持 normal 或 lucky")
    private String type;

    @NotNull(message = "金额不能为空")
    @DecimalMin(value = "0.01", message = "红包金额最小为0.01")
    private java.math.BigDecimal totalAmount;

    @NotNull(message = "红包个数不能为空")
    @Min(value = 1, message = "红包个数最少为1")
    @Max(value = 100, message = "红包个数最多为100")
    private Integer totalCount;

    private String greeting;
}
