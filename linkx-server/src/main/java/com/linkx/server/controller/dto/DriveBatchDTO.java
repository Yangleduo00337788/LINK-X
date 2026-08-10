package com.linkx.server.controller.dto;


/**
 * 作者：yangleduo
 */
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class DriveBatchDTO {
    /** 兼容：同类型批量 */
    @Size(max = 200, message = "批量操作最多200项")
    private List<@NotBlank(message = "id 不能为空") String> ids;

    @Pattern(regexp = "^(file|folder)$", message = "kind 仅支持 file 或 folder")
    private String kind;

    /** 混合类型批量（优先） */
    @Size(max = 200, message = "批量操作最多200项")
    private List<DriveBatchItem> items;

    /** 移动目标文件夹，空/null=根目录 */
    @Size(max = 64, message = "targetFolderId 最多64字符")
    private String targetFolderId;

    @Data
    public static class DriveBatchItem {
        @NotBlank
        @Pattern(regexp = "^(file|folder)$", message = "kind 仅支持 file 或 folder")
        private String kind;

        @NotBlank
        private String id;
    }
}
