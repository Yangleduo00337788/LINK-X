package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 客户端注入的当前 IM 会话上下文，供灵伴理解用户正在聊什么。
 */
@Data
public class LinkMateImContextDTO {

    private String conversationId;

    @Size(max = 200)
    private String title;

    @JsonAlias("isGroup")
    private Boolean group;

    @Valid
    @Size(max = 30)
    private List<ImMessageItem> messages;

    @Data
    public static class ImMessageItem {

        @Size(max = 100)
        private String sender;

        @Size(max = 2000)
        private String content;

        @Size(max = 32)
        private String time;

        private Boolean self;
    }
}
