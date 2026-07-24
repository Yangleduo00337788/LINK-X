package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 合规数据清除：需登录密码二次确认，防止会话被盗后一键清空。
 */
@Data
public class CompliancePurgeDTO {

    @NotBlank(message = "请输入登录密码以确认清除数据")
    private String password;
}
