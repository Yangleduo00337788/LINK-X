package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FeedbackDTO {

    @NotBlank(message = "反馈类型不能为空")
    @Pattern(regexp = "bug|suggestion|other", message = "反馈类型必须为 bug、suggestion 或 other")
    private String type;

    @NotBlank(message = "反馈内容不能为空")
    @Size(max = 2000, message = "反馈内容不能超过 2000 字")
    private String content;

    @Size(max = 100, message = "联系方式不能超过 100 字")
    private String contact;
}
