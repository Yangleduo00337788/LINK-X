package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CommentShortVideoDTO {

    @Size(max = 500, message = "评论最多500字")
    private String content;

    private Long parentId;

    @Size(max = 512, message = "图片键过长")
    private String imageKey;

    @Size(max = 20, message = "@用户过多")
    private List<Long> mentions;
}
