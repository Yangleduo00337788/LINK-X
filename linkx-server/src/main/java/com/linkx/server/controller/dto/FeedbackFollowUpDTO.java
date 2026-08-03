package com.linkx.server.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "用户追评/补充说明")
public class FeedbackFollowUpDTO {

    @NotBlank
    @Size(max = 2000)
    @Schema(description = "补充内容")
    private String content;
}
