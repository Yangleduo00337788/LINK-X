package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批量禁言/解禁群成员 DTO
 */
@Data
public class BatchMuteMembersDTO {

    @NotNull(message = "memberIds 不能为空")
    @Size(min = 1, max = 100, message = "单次最多操作100个成员")
    private List<@NotNull Long> memberIds;

    @NotNull(message = "muted 不能为空")
    private Boolean muted;
}
