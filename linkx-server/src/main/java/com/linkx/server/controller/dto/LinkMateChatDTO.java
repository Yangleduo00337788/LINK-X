package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LinkMateChatDTO {

  /** 会话 ID；首次对话可留空，服务端自动创建 */
  private String sessionId;

  @Size(max = 8000, message = "消息内容过长")
  private String message;

  /** 是否开启深度思考（仅模型支持时生效） */
  private Boolean deepThinking;

  /** 重新生成：基于 regenerateMessageId 对应的助手回复重新请求 */
  private Boolean regenerate;

  /** 待重新生成的助手消息 ID（须为会话中最后一条助手消息） */
  private String regenerateMessageId;

  /** 当前 IM 聊天上下文（可选） */
  @Valid
  private LinkMateImContextDTO imContext;
}
