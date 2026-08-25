package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "管理端短视频评论查询")
public class AdminShortVideoCommentQueryDTO extends AdminPageQueryDTO {

    @Schema(description = "作品 ID")
    private Long postId;

    @Schema(description = "评论用户 ID")
    private Long userId;
}
