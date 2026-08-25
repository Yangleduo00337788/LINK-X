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
public class AdminShortVideoTopicVO {

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    private String name;

    private String displayName;

    private Integer postCount;

    private Boolean pinned;

    private Integer pinOrder;

    private Integer status;

    private Double hotScore;

    private String lastPostAt;

    private String createTime;
}
