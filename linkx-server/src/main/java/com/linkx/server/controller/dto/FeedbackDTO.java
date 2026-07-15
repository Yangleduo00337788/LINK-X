package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FeedbackDTO {

    @NotNull(message = "反馈类型不能为空")
    private String type;

    @NotBlank(message = "反馈内容不能为空")
    private String content;

    private String contact;
}
