package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LinkMateChatDTO {

  /** 会话 ID；首次对话可留空，服务端自动创建 */
  private String sessionId;

  @NotBlank(message = "消息内容不能为空")
  @Size(max = 8000, message = "消息内容过长")
  private String message;

  /** 是否开启深度思考（仅模型支持时生效） */
  private Boolean deepThinking;
}
