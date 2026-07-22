package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class DriveBatchDTO {
    /** 兼容：同类型批量 */
    private List<String> ids;
    private String kind;

    /** 混合类型批量（优先） */
    private List<DriveBatchItem> items;

    /** 移动目标文件夹，空/null=根目录 */
    private String targetFolderId;

    @Data
    public static class DriveBatchItem {
        @NotBlank
        private String kind;
        @NotBlank
        private String id;
    }
}
