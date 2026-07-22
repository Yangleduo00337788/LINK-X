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
public class MomentsPostVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    private String nickname;

    private String avatar;

    private String content;

    private List<String> images;

    private String location;

    /** 提醒谁看：用户 ID JSON 字符串 */
    private String atUsers;

    /** 提醒谁看：昵称列表（便于前端直接展示） */
    private List<String> atUserNames;

    private Integer visibility;

    private String time;

    private Integer likes;

    private boolean liked;

    private List<String> likedBy;

    private List<MomentsCommentVO> comments;
}
