package com.linkx.server.controller.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 更新朋友圈动态请求
 */
@Data
public class UpdateMomentsDTO {

    @Size(max = 2000, message = "动态内容最多2000字")
    private String content;

    /** 媒体 object key 列表（全量替换；null 表示不改媒体） */
    private List<String> images;

    private String location;

    private List<Long> atUsers;

    /** 可见性：0=公开，1=仅好友，2=私密 */
    private Integer visibility;
}
