package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class ShareShortVideoChatDTO {

    @NotEmpty(message = "请选择会话")
    @Size(max = 20, message = "一次最多分享至 20 个会话")
    private List<String> conversationIds;

    @Size(max = 500, message = "留言过长")
    private String leaveMessage;
}
