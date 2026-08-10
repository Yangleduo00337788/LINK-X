package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "设备会话列表查询")
public class AdminDeviceQueryDTO extends AdminPageQueryDTO {

    @Schema(description = "设备类型筛选")
    private String deviceType;

    @Schema(description = "按用户 ID 精确筛选")
    private Long userId;
}
