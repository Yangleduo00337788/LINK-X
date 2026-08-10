package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "反馈回复")
public class AdminFeedbackReplyDTO {

    @Schema(description = "回复内容")
    @NotBlank
    @Size(max = 2000)
    private String content;
}
