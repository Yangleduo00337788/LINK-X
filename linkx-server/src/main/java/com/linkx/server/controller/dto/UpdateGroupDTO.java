package com.linkx.server.controller.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新群信息请求
 */
@Data
public class UpdateGroupDTO {

    /**
     * 新群名称（可选）
     */
    @Size(max = 100, message = "群名称最多100字")
    private String name;

    /**
     * 群公告（可选）
     */
    @Size(max = 2000, message = "群公告最多2000字")
    private String announcement;
}
