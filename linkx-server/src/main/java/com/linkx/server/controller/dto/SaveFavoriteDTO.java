package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SaveFavoriteDTO {

    @Size(max = 200)
    private String title;

    @NotBlank(message = "收藏内容不能为空")
    @Size(max = 100000)
    private String content;

    @Pattern(regexp = "^(note|image|link|file|message)$", message = "type 无效")
    private String type;

    @Size(max = 32)
    private String sourceType;

    @Size(max = 64)
    private String sourceId;

    /** JSON 数组字符串，如 ["工作","学习"] */
    @Size(max = 500)
    private String tags;

    private Long fileSize;
}
