package com.linkx.server.controller.dto;

import lombok.Data;

/**
 * 更新群信息请求
 */
@Data
public class UpdateGroupDTO {

    /**
     * 新群名称（可选）
     */
    private String name;

    /**
     * 群公告（可选）
     */
    private String announcement;
}
