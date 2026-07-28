package com.linkx.server.controller.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @DecimalMax(value = "100000", message = "红包金额最大为10万元")
    private java.math.BigDecimal totalAmount;

    @NotNull(message = "红包个数不能为空")
    @Min(value = 1, message = "红包个数最少为1")
    @Max(value = 100, message = "红包个数最多为100")
    private Integer totalCount;

    @Size(max = 200, message = "祝福语最长200字符")
    private String greeting;

    /**
     * 客户端幂等ID：防止网络重试/双击导致重复发红包。
     * 同一用户同一 clientMsgId 仅生效一次。
     */
    @NotBlank(message = "clientMsgId不能为空")
    @Size(max = 128, message = "clientMsgId最长128字符")
    private String clientMsgId;
}
