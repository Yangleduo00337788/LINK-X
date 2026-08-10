package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量移除群成员 DTO
 */
@Data
public class BatchRemoveMembersDTO {

    @NotNull(message = "memberIds 不能为空")
    @Size(min = 1, max = 100, message = "单次最多操作100个成员")
    private List<@NotNull Long> memberIds;
}
