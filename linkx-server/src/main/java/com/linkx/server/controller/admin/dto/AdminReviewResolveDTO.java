package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "审核处置请求")
public class AdminReviewResolveDTO {

    @Schema(description = "处理意见")
    @Size(max = 1000)
    private String resolution;

    @Schema(description = "用户处置：none / freeze / ban（仅审核通过时生效）")
    @Size(max = 16)
    private String userAction;

    @Schema(description = "内容处置：none / delete（仅审核通过时生效；按目标类型删除/撤回）")
    @Size(max = 16)
    private String contentAction;

    @Schema(description = "群处置：none / dissolve / freeze_owner / ban_owner（仅目标为群且审核通过时生效）")
    @Size(max = 32)
    private String groupAction;
}
