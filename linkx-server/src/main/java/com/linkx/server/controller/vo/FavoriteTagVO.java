package com.linkx.server.controller.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FavoriteTagVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;
    private String color;
    private Integer sortOrder;
    private Boolean preset;
    /** 引用该标签的收藏数量 */
    private Integer count;
}
