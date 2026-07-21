package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateGroupAnnouncementDTO {

    @NotBlank(message = "公告内容不能为空")
    @Size(max = 5000, message = "公告内容最多5000字")
    private String content;

    /** 是否置顶（可选，默认 false） */
    private Boolean pinned;
}
