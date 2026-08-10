package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "异常访问记录查询")
public class AdminAbnormalAccessQueryDTO extends AdminPageQueryDTO {

    @Schema(description = "来源：all / login_fail / rate_limit / risk_event")
    private String source;

    @Schema(description = "IP 过滤（精确或前缀）")
    private String ip;
}
