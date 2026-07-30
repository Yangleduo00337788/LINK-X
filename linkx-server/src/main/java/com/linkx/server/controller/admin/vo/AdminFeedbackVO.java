package com.linkx.server.controller.admin.vo;

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
    private Date createTime;
}
