package com.linkx.server.controller.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 申请入群 DTO
 */
@Data
public class RequestJoinDTO {

    @Size(max = 200, message = "申请理由最长200字符")
    private String message;
}
