package com.linkx.server.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发布朋友圈动态请求
 */
@Data
public class PublishMomentsDTO {

    @Size(max = 2000, message = "动态内容最多2000字")
    private String content;

    /**
     * 图片/视频 object key 列表
     */
    @Size(max = 9, message = "最多上传9张图片/视频")
    private List<@NotBlank(message = "object key 不能为空") @Pattern(regexp = "^[A-Za-z0-9_\\-/.]{1,256}$", message = "object key 格式非法") String> images;

    /**
     * 所在位置
     */
    @Size(max = 128, message = "位置最多128字")
    private String location;

    /**
     * 提醒谁看，用户 ID 列表
     */
    @Size(max = 50, message = "提醒看的好友最多50个")
    private List<Long> atUsers;

    /**
     * 可见性：0=公开，1=仅好友，2=私密
     */
    @Min(value = 0, message = "可见性非法")
    @Max(value = 2, message = "可见性非法")
    private Integer visibility;
}
