package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 评论朋友圈请求
 */
@Data
public class CommentMomentsDTO {

    @NotBlank(message = "评论内容不能为空")
    private String content;

    /**
     * 父评论 ID（可选，用于回复）
     */
    private Long parentId;

    /**
     * 被 @ 的用户 ID 列表（可选）
     */
    private List<Long> mentions;
}
