package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "黑名单列表查询")
public class AdminBlacklistQueryDTO extends AdminPageQueryDTO {

    @Schema(description = "状态：active/released，空表示全部")
    private String entryStatus;
}
