package com.linkx.server.controller.admin.vo;

import com.linkx.server.controller.vo.FeedbackReplyVO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
@Builder
@Schema(description = "反馈信息")
public class AdminFeedbackVO {

    private Long id;
    private Long userId;
    private String username;
    private String type;
    private String content;
    private String contact;
    private String status;
    private String reply;
    private Date replyTime;
    private Date createTime;
    @Schema(description = "是否超过反馈 SLA 仍未处理（仅 pending）")
    private Boolean overdue;
    private Long assigneeId;
    private String assigneeName;
    private Date assignedAt;
    @Schema(description = "多轮回复记录（详情接口返回）")
    private List<FeedbackReplyVO> replies;
}
