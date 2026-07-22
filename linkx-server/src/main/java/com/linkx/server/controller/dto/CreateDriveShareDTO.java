package com.linkx.server.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateDriveShareDTO {
    /** file / folder */
    @NotBlank
    private String shareType;

    @NotBlank
    private String targetId;

    /** 可选提取码 */
    private String password;

    /** 有效小时数，空=永久 */
    private Integer expireHours;

    /** 最大下载次数，空=不限 */
    private Integer maxDownloads;
}
