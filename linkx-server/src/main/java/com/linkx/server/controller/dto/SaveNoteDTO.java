package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SaveNoteDTO {

    @Size(max = 200, message = "标题最多200字")
    private String title;

    @NotBlank(message = "笔记内容不能为空")
    @Size(max = 100000, message = "笔记内容过多")
    private String content;

    /**
     * 类型：note(普通笔记) / image(图片收藏) / link(链接收藏) / file(文件收藏)
     */
    @Pattern(regexp = "^(note|image|link|file)$", message = "type 必须为 note/image/link/file")
    private String type;
}
