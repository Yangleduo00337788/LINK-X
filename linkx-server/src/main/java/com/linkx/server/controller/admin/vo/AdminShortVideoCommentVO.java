package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminShortVideoCommentVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long postId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String username;

    private String nickname;

    private String content;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    private Integer likeCount;

    private String createTime;

    /** 所属作品封面（同源媒体代理地址） */
    private String postCoverUrl;
}
