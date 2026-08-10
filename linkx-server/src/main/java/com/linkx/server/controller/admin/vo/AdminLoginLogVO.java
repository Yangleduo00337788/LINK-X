package com.linkx.server.controller.admin.vo;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.util.Date;

@Data
@Builder
@Schema(description = "登录日志")
public class AdminLoginLogVO {

    private Long id;
    private Long userId;
    private String username;
    private String ip;
    @Schema(description = "IP 归属地")
    private String region;
    private String userAgent;
    private Integer success;
    private String reason;
    private Date createTime;
}
