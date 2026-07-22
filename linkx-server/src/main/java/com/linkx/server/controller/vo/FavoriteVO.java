package com.linkx.server.controller.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FavoriteVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String title;
    private String content;
    private String type;
    private String sourceType;
    private String sourceId;
    /** JSON 数组字符串 */
    private String tags;
    private Long fileSize;
    private String createTime;
    private String updateTime;
}
