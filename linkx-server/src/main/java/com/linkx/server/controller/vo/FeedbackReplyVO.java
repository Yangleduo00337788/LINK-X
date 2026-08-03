package com.linkx.server.controller.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "反馈回复记录")
public class FeedbackReplyVO {

    private Long id;
    private Long feedbackId;
    @Schema(description = "admin|user")
    private String senderType;
    private Long senderId;
    private String senderName;
    private String content;
    private Date createTime;
}
