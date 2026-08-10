package com.linkx.server.controller.admin.dto;


/**
 * 作者：yangleduo
 */
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "菜单排序")
public class AdminMenuReorderDTO {

    @NotEmpty
    @Valid
    @Schema(description = "排序项（同级或跨级均可）")
    private List<Item> items;

    @Data
    @Schema(description = "单个菜单排序项")
    public static class Item {

        @NotNull
        @Schema(description = "菜单ID")
        private Long id;

        @Schema(description = "父菜单ID，null 表示不改父级")
        private Long parentId;

        @NotNull
        @Schema(description = "排序值，越小越靠前")
        private Integer sortOrder;
    }
}
