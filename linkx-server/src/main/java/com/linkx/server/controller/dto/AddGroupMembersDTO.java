package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 添加群成员请求
 */
@Data
public class AddGroupMembersDTO {

    @NotNull(message = "成员列表不能为空")
    private List<Long> memberIds;
}
