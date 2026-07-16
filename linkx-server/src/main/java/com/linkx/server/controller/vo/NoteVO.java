package com.linkx.server.controller.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NoteVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String title;

    private String content;

    /**
     * 类型：note(普通笔记) / image(图片收藏) / link(链接收藏) / file(文件收藏)
     */
    private String type;

    private String createTime;

    private String updateTime;
}
