package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 设置群聊邀请策略 DTO
 */
@Data
public class SetInvitePolicyDTO {

    @NotBlank(message = "policy 不能为空")
    @Pattern(regexp = "^(ownerApprove|anyMember)$", message = "邀请策略仅支持 ownerApprove 或 anyMember")
    private String policy;
}
