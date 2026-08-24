package com.linkx.server.controller.vo;


/**
 * 作者：yangleduo
 */
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
public class ShortVideoPostVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String nickname;

    private String avatar;

    private String description;

    private String videoUrl;

    private String coverUrl;

    private Integer durationMs;

    private Integer visibility;

    private Long playCount;

    private Long shares;

    private String time;

    private Integer likes;

    private boolean liked;

    private Integer favorites;

    private boolean favorited;

    private boolean followingAuthor;

    private Integer commentCount;

    private List<ShortVideoCommentVO> comments;
}
