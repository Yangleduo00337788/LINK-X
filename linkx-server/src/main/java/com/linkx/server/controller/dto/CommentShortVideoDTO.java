package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CommentShortVideoDTO {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 500, message = "评论最多500字")
    private String content;

    private Long parentId;

    @Size(max = 20, message = "@用户过多")
    private List<Long> mentions;
}
