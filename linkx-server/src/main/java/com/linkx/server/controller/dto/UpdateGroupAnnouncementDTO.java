package com.linkx.server.controller.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateGroupAnnouncementDTO {

    @Size(max = 5000, message = "公告内容最多5000字")
    private String content;

    /** 是否置顶；传 null 表示不修改 */
    private Boolean pinned;
}
