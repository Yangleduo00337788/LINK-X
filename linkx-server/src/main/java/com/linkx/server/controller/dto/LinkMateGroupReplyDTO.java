package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LinkMateGroupReplyDTO {

    @NotNull(message = "会话 ID 不能为空")
    private Long conversationId;

    @NotBlank(message = "提问内容不能为空")
    @Size(max = 4000, message = "提问内容过长")
    private String question;
}
