package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 取消通话请求
 */
@Data
public class CallCancelDTO {

    @NotBlank(message = "通话ID不能为空")
    private String callId;

    /**
     * 取消原因：空=主动取消；{@code timeout}=振铃超时未接听。
     */
    private String reason;
}
