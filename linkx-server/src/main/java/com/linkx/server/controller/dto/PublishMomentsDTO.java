package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发布朋友圈动态请求
 */
@Data
public class PublishMomentsDTO {

    @NotBlank(message = "动态内容不能为空")
    @Size(max = 2000, message = "动态内容最多2000字")
    private String content;

    /**
     * 图片 URL 列表
     */
    private List<String> images;

    /**
     * 所在位置
     */
    private String location;

    /**
     * 提醒谁看，用户 ID 列表
     */
    private List<Long> atUsers;

    /**
     * 可见性：0=公开，1=仅好友，2=私密
     */
    private Integer visibility;
}
