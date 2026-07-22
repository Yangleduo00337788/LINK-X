package com.linkx.server.controller.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DriveItemVO {
    /** folder / file */
    private String kind;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long parentId;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long folderId;

    private Long fileSize;
    private String fileUrl;
    private String contentType;
    private String ext;
    private String category;
    private String description;
    private Integer childCount;
    private List<String> tags;
    private String uploaderName;
    /** 上传者头像（可访问 URL） */
    private String uploaderAvatar;
    private Long createTime;
    private Long updateTime;
}
