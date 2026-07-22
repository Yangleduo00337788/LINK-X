package com.linkx.server.controller.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MomentsCommentVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String nickname;

    private String avatar;

    private String content;

    private String time;

    /**
     * 被 @ 的用户 ID 列表（已去重且剔除评论者自身）
     */
    private List<Long> mentions;

    /** 回复的父评论 ID（嵌套回复） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    /** 被回复者昵称（有 parentId 时填充） */
    private String replyToNickname;
}
