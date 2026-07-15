package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建群聊请求
 */
@Data
public class CreateGroupDTO {

    @NotBlank(message = "群名称不能为空")
    @Size(max = 50, message = "群名称最多50个字符")
    private String name;

    /**
     * 群成员用户 ID 列表（不含创建者）
     */
    @NotNull(message = "成员列表不能为空")
    @Size(min = 1, message = "群聊至少需要1名成员")
    private List<Long> memberIds;
}
