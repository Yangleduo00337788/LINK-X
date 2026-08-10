package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 添加群成员请求
 */
@Data
public class AddGroupMembersDTO {

    @NotNull(message = "成员列表不能为空")
    @Size(max = 100, message = "单次最多添加100个成员")
    private List<@NotNull(message = "成员 ID 不能为空") @Positive(message = "成员 ID 必须为正数") Long> memberIds;
}
