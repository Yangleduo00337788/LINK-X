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
public class AdminShortVideoPostVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String username;

    private String nickname;

    private String description;

    private Integer visibility;

    private Long playCount;

    private Long shareCount;

    private Integer likeCount;

    private Integer favoriteCount;

    private Integer commentCount;

    private Integer durationMs;

    private String transcodeStatus;

    private String videoUrl;

    private String coverUrl;

    private String createTime;
}
