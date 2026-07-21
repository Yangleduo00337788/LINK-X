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
     * 类型：仅普通笔记（收藏请走 /favorites）
     */
    @Pattern(regexp = "^(note)?$", message = "笔记 type 仅支持 note")
    private String type;
}
